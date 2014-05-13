package com.ericguerra.shopkeep.service;

import com.ericguerra.shopkeep.domain.PaymentMethod;
import com.ericguerra.shopkeep.domain.Product;
import com.ericguerra.shopkeep.domain.Sale;
import com.ericguerra.shopkeep.domain.StockMovement;
import com.ericguerra.shopkeep.repo.ProductRepository;
import com.ericguerra.shopkeep.repo.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CheckoutServiceTest {
    static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.now());

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(NOW.get(), zone); }
                @Override public Instant instant() { return NOW.get(); }
            };
        }
    }

    @Autowired CheckoutService checkout;
    @Autowired InventoryService inventory;
    @Autowired ProductRepository products;
    @Autowired SaleRepository sales;
    @Autowired ReportService reports;

    private Product product(String sku) {
        return products.findBySku(sku).orElseThrow();
    }

    private Sale sell(PaymentMethod method, Long tendered, CheckoutService.CartLine... lines) {
        return checkout.checkout(new CheckoutService.CheckoutRequest(List.of(lines), 0, method, tendered), "cashier");
    }

    @Test
    void cashSaleDeductsStockAndRecordsLedger() {
        Product mug = product("MUG-LOGO");
        int before = mug.getStockOnHand();

        Sale sale = sell(PaymentMethod.CASH, 5000L, new CheckoutService.CartLine(mug.getId(), 2, 0));

        assertEquals(2900, sale.getSubtotalCents());
        assertEquals(239, sale.getTaxCents());              // 2900 * 8.25% = 239.25
        assertEquals(3139, sale.getTotalCents());
        assertEquals(5000 - 3139, sale.getChangeCents());
        assertTrue(sale.getReceiptNo().matches("R\\d{6}-\\d{4}"));
        assertEquals(before - 2, product("MUG-LOGO").getStockOnHand());

        var history = inventory.history(mug.getId());
        assertEquals(StockMovement.Reason.SALE, history.get(0).getReason());
        assertEquals(sale.getReceiptNo(), history.get(0).getReference());
    }

    @Test
    void duplicateCartLinesAreMergedBeforeStockCheck() {
        Product grinder = product("GRINDER-HAND"); // 3 on hand
        PosException e = assertThrows(PosException.class, () -> sell(PaymentMethod.CARD, null,
                new CheckoutService.CartLine(grinder.getId(), 2, 0),
                new CheckoutService.CartLine(grinder.getId(), 2, 0)));
        assertEquals("INSUFFICIENT_STOCK", e.getCode());
        assertEquals(3, product("GRINDER-HAND").getStockOnHand(), "failed checkout must not touch stock");
    }

    @Test
    void cashRequiresEnoughTendered() {
        Product filters = product("FILTER-100");
        PosException e = assertThrows(PosException.class, () -> sell(PaymentMethod.CASH, 100L, new CheckoutService.CartLine(filters.getId(), 1, 0)));
        assertEquals("INVALID", e.getCode());
    }

    @Test
    void partialReturnsRefundProRataAndRestock() {
        Product mug = product("MUG-LOGO");
        Sale sale = sell(PaymentMethod.CARD, null, new CheckoutService.CartLine(mug.getId(), 3, 0));
        long lineId = sale.getLines().get(0).getId();
        int stockAfterSale = product("MUG-LOGO").getStockOnHand();

        Sale afterOne = checkout.processReturn(sale.getReceiptNo(), List.of(new CheckoutService.ReturnLine(lineId, 1)), "cashier");
        assertEquals(Sale.Status.PARTIALLY_RETURNED, afterOne.getStatus());
        long firstRefund = afterOne.getRefundedCents();

        Sale afterAll = checkout.processReturn(sale.getReceiptNo(), List.of(new CheckoutService.ReturnLine(lineId, 2)), "cashier");
        assertEquals(Sale.Status.RETURNED, afterAll.getStatus());
        assertEquals(sale.getTotalCents(), afterAll.getRefundedCents(), "full return refunds exactly what was paid");
        assertTrue(firstRefund > 0 && firstRefund < sale.getTotalCents());
        assertEquals(stockAfterSale + 3, product("MUG-LOGO").getStockOnHand());

        PosException tooMany = assertThrows(PosException.class, () ->
                checkout.processReturn(sale.getReceiptNo(), List.of(new CheckoutService.ReturnLine(lineId, 1)), "cashier"));
        assertEquals("RETURN_EXCEEDS_SALE", tooMany.getCode());
    }

    @Test
    void voidRestoresStockOnlyWithinWindow() {
        Product decaf = product("COF-DECAF-12");
        int before = decaf.getStockOnHand();
        Sale sale = sell(PaymentMethod.CARD, null, new CheckoutService.CartLine(decaf.getId(), 1, 0));

        Sale voided = checkout.voidSale(sale.getReceiptNo(), "manager");
        assertEquals(Sale.Status.VOIDED, voided.getStatus());
        assertEquals(before, product("COF-DECAF-12").getStockOnHand());

        Sale later = sell(PaymentMethod.CARD, null, new CheckoutService.CartLine(decaf.getId(), 1, 0));
        Instant saved = NOW.get();
        try {
            NOW.set(saved.plus(Duration.ofHours(1)));
            PosException e = assertThrows(PosException.class, () -> checkout.voidSale(later.getReceiptNo(), "manager"));
            assertEquals("VOID_WINDOW_PASSED", e.getCode());
        } finally {
            NOW.set(saved);
        }
    }

    @Test
    void inventoryAdjustmentsCannotGoNegative() {
        Product t = product("TUMBLER-16");
        assertThrows(PosException.class, () -> inventory.adjust(t.getId(), -1000, StockMovement.Reason.DAMAGE, "x", "manager"));
        assertThrows(PosException.class, () -> inventory.adjust(t.getId(), 1, StockMovement.Reason.SALE, "x", "manager"));
        Product counted = inventory.count(t.getId(), 10, "manager");
        assertEquals(10, counted.getStockOnHand());
        assertEquals(StockMovement.Reason.COUNT, inventory.history(t.getId()).get(0).getReason());
    }

    @Test
    void dailyReportExcludesVoidedSales() {
        Product filters = product("FILTER-100");
        sell(PaymentMethod.CARD, null, new CheckoutService.CartLine(filters.getId(), 4, 0));
        Sale voided = sell(PaymentMethod.CARD, null, new CheckoutService.CartLine(filters.getId(), 1, 0));
        checkout.voidSale(voided.getReceiptNo(), "manager");

        var report = reports.daily(java.time.LocalDate.now(java.time.ZoneId.of("America/Chicago")));
        assertTrue(report.transactions() >= 1);
        assertTrue(report.topItems().stream().anyMatch(i -> i.sku().equals("FILTER-100") && i.units() >= 4));
        assertEquals(report.netSalesCents() - report.costOfGoodsCents(), report.grossMarginCents());
    }
}
