package com.ericguerra.shopkeep.service;

import com.ericguerra.shopkeep.domain.PaymentMethod;
import com.ericguerra.shopkeep.domain.Product;
import com.ericguerra.shopkeep.domain.Sale;
import com.ericguerra.shopkeep.domain.SaleLine;
import com.ericguerra.shopkeep.domain.StockMovement;
import com.ericguerra.shopkeep.repo.ProductRepository;
import com.ericguerra.shopkeep.repo.SaleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CheckoutService {
    public record CartLine(Long productId, int quantity, int discountPercent) {}

    public record CheckoutRequest(List<CartLine> lines, long orderDiscountCents, PaymentMethod paymentMethod, Long tenderedCents) {}

    public record ReturnLine(Long saleLineId, int quantity) {}

    private static final Duration VOID_WINDOW = Duration.ofMinutes(30);
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final ProductRepository products;
    private final SaleRepository sales;
    private final InventoryService inventory;
    private final PriceCalculator calculator;
    private final boolean allowBackorder;
    private final Clock clock;
    private final ZoneId zone;

    public CheckoutService(ProductRepository products, SaleRepository sales, InventoryService inventory,
                           @Value("${shopkeep.tax-rate:0.0825}") BigDecimal taxRate,
                           @Value("${shopkeep.allow-backorder:false}") boolean allowBackorder,
                           @Value("${shopkeep.zone:America/Chicago}") String zone,
                           Clock clock) {
        this.products = products;
        this.sales = sales;
        this.inventory = inventory;
        this.calculator = new PriceCalculator(taxRate);
        this.allowBackorder = allowBackorder;
        this.zone = ZoneId.of(zone);
        this.clock = clock;
    }

    /** Price-only quote for the register display: no locks, no writes. */
    @Transactional(readOnly = true)
    public PriceCalculator.Totals quote(List<CartLine> cart, long orderDiscountCents) {
        Map<Long, Product> byId = products.findAllById(cart.stream().map(CartLine::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return calculator.price(inputs(cart, byId), orderDiscountCents);
    }

    @Transactional
    public Sale checkout(CheckoutRequest req, String cashier) {
        if (req.lines() == null || req.lines().isEmpty()) throw PosException.invalid("cart is empty");
        Map<Long, CartLine> merged = merge(req.lines());

        Map<Long, Product> locked = products.lockAll(merged.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Map<String, Object>> shortages = new ArrayList<>();
        for (CartLine line : merged.values()) {
            Product p = locked.get(line.productId());
            if (p == null || !p.isActive()) throw PosException.notFound("Product", line.productId());
            if (!allowBackorder && p.getStockOnHand() < line.quantity()) {
                shortages.add(Map.of("productId", p.getId(), "sku", p.getSku(), "requested", line.quantity(), "onHand", p.getStockOnHand()));
            }
        }
        if (!shortages.isEmpty()) throw PosException.conflict("INSUFFICIENT_STOCK", "not enough stock", Map.of("shortages", shortages));

        List<CartLine> cart = new ArrayList<>(merged.values());
        PriceCalculator.Totals totals = calculator.price(inputs(cart, locked), req.orderDiscountCents());

        long tendered;
        long change;
        if (req.paymentMethod() == PaymentMethod.CASH) {
            if (req.tenderedCents() == null) throw PosException.invalid("cash sales need a tendered amount");
            try {
                change = PriceCalculator.change(totals.totalCents(), req.tenderedCents());
            } catch (IllegalArgumentException e) {
                throw PosException.invalid(e.getMessage());
            }
            tendered = req.tenderedCents();
        } else {
            tendered = totals.totalCents();
            change = 0;
        }

        Sale sale = new Sale(nextReceiptNo(), cashier, req.paymentMethod());
        for (int i = 0; i < cart.size(); i++) {
            CartLine c = cart.get(i);
            Product p = locked.get(c.productId());
            PriceCalculator.LineResult r = totals.lines().get(i);
            sale.addLine(new SaleLine(p.getId(), p.getSku(), p.getName(), p.getPriceCents(), p.getCostCents(),
                    c.quantity(), r.discountCents(), r.taxCents()));
        }
        sale.setTotals(totals.subtotalCents(), totals.discountCents(), totals.taxCents(), totals.totalCents(), tendered, change);
        sales.save(sale);

        for (CartLine c : cart) {
            inventory.apply(locked.get(c.productId()), -c.quantity(), StockMovement.Reason.SALE, sale.getReceiptNo(), cashier);
        }
        return sale;
    }

    /** Partial or full return. Refund = the line's paid amount (after discounts, incl. tax) pro-rated by quantity. */
    @Transactional
    public Sale processReturn(String receiptNo, List<ReturnLine> returns, String user) {
        Sale sale = sales.findByReceiptNo(receiptNo).orElseThrow(() -> PosException.notFound("Sale", receiptNo));
        if (sale.getStatus() == Sale.Status.VOIDED) throw PosException.conflict("VOIDED", "voided sales cannot be returned", Map.of());
        if (returns == null || returns.isEmpty()) throw PosException.invalid("nothing to return");

        Map<Long, SaleLine> lines = sale.getLines().stream().collect(Collectors.toMap(SaleLine::getId, Function.identity()));
        Map<Long, Product> locked = products.lockAll(sale.getLines().stream().map(SaleLine::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        long refund = 0;
        for (ReturnLine r : returns) {
            SaleLine line = lines.get(r.saleLineId());
            if (line == null) throw PosException.notFound("Sale line", r.saleLineId());
            if (r.quantity() <= 0 || r.quantity() > line.returnable()) {
                throw PosException.conflict("RETURN_EXCEEDS_SALE", "cannot return " + r.quantity() + " of " + line.getSku(),
                        Map.of("returnable", line.returnable()));
            }
            long paid = line.netCents() + line.getTaxCents();
            // Pro-rate; the final unit returned picks up any rounding remainder.
            long alreadyRefunded = paid * line.getReturnedQuantity() / line.getQuantity();
            line.markReturned(r.quantity());
            long refundedAfter = paid * line.getReturnedQuantity() / line.getQuantity();
            if (line.returnable() == 0) refundedAfter = paid;
            refund += refundedAfter - alreadyRefunded;

            Product p = locked.get(line.getProductId());
            if (p != null) inventory.apply(p, r.quantity(), StockMovement.Reason.RETURN, receiptNo, user);
        }
        sale.recordRefund(refund);
        return sale;
    }

    /** Managers can void a sale shortly after it was rung up (e.g. wrong items); stock goes back. */
    @Transactional
    public Sale voidSale(String receiptNo, String manager) {
        Sale sale = sales.findByReceiptNo(receiptNo).orElseThrow(() -> PosException.notFound("Sale", receiptNo));
        if (sale.getStatus() != Sale.Status.COMPLETED) throw PosException.conflict("NOT_VOIDABLE", "sale is " + sale.getStatus(), Map.of());
        if (Duration.between(sale.getCreatedAt(), Instant.now(clock)).compareTo(VOID_WINDOW) > 0) {
            throw PosException.conflict("VOID_WINDOW_PASSED", "sales can only be voided within 30 minutes; process a return instead", Map.of());
        }
        Map<Long, Product> locked = products.lockAll(sale.getLines().stream().map(SaleLine::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (SaleLine line : sale.getLines()) {
            Product p = locked.get(line.getProductId());
            if (p != null) inventory.apply(p, line.getQuantity(), StockMovement.Reason.VOID, receiptNo, manager);
        }
        sale.voidSale();
        return sale;
    }

    private static Map<Long, CartLine> merge(List<CartLine> lines) {
        Map<Long, CartLine> merged = new LinkedHashMap<>();
        for (CartLine l : lines) {
            if (l.productId() == null) throw PosException.invalid("productId is required");
            if (l.quantity() <= 0) throw PosException.invalid("quantity must be positive");
            merged.merge(l.productId(), l, (a, b) -> new CartLine(a.productId(), a.quantity() + b.quantity(), Math.max(a.discountPercent(), b.discountPercent())));
        }
        return merged;
    }

    private static List<PriceCalculator.LineInput> inputs(List<CartLine> cart, Map<Long, Product> byId) {
        List<PriceCalculator.LineInput> out = new ArrayList<>();
        for (CartLine c : cart) {
            Product p = byId.get(c.productId());
            if (p == null) throw PosException.notFound("Product", c.productId());
            try {
                out.add(new PriceCalculator.LineInput(p.getPriceCents(), c.quantity(), c.discountPercent(), p.isTaxable()));
            } catch (IllegalArgumentException e) {
                throw PosException.invalid(e.getMessage());
            }
        }
        return out;
    }

    private String nextReceiptNo() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfDay = today.atStartOfDay(zone).toInstant();
        long seq = sales.countSince(startOfDay) + 1;
        return "R" + RECEIPT_DATE.format(today) + "-" + String.format("%04d", seq);
    }
}
