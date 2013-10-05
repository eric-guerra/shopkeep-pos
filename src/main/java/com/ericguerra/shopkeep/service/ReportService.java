package com.ericguerra.shopkeep.service;

import com.ericguerra.shopkeep.domain.PaymentMethod;
import com.ericguerra.shopkeep.domain.Sale;
import com.ericguerra.shopkeep.domain.SaleLine;
import com.ericguerra.shopkeep.repo.SaleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    public record TopItem(String sku, String name, int units, long revenueCents) {}

    /** End-of-day ("Z") report. Gross margin uses the cost snapshot stored on each line. */
    public record DailyReport(LocalDate date, int transactions, long grossSalesCents, long discountCents, long taxCents,
                              long refundsCents, long netSalesCents, long costOfGoodsCents, long grossMarginCents,
                              Map<PaymentMethod, Long> tenderTotals, List<TopItem> topItems) {}

    private final SaleRepository sales;
    private final ZoneId zone;

    public ReportService(SaleRepository sales, @Value("${shopkeep.zone:America/Chicago}") String zone) {
        this.sales = sales;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public DailyReport daily(LocalDate date) {
        List<Sale> day = sales.findCountedBetween(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
        long gross = 0, discount = 0, tax = 0, refunds = 0, cogs = 0;
        Map<PaymentMethod, Long> tenders = new EnumMap<>(PaymentMethod.class);
        Map<String, TopItem> items = new HashMap<>();
        for (Sale s : day) {
            gross += s.getSubtotalCents();
            discount += s.getDiscountCents();
            tax += s.getTaxCents();
            refunds += s.getRefundedCents();
            tenders.merge(s.getPaymentMethod(), s.getTotalCents() - s.getRefundedCents(), Long::sum);
            for (SaleLine l : s.getLines()) {
                int kept = l.getQuantity() - l.getReturnedQuantity();
                cogs += l.getUnitCostCents() * kept;
                long revenue = l.netCents() * kept / l.getQuantity();
                items.merge(l.getSku(), new TopItem(l.getSku(), l.getName(), kept, revenue),
                        (a, b) -> new TopItem(a.sku(), a.name(), a.units() + b.units(), a.revenueCents() + b.revenueCents()));
            }
        }
        // Net sales exclude tax. Refunds include tax, so subtract only the pre-tax share of returned lines.
        long refundedNet = day.stream().flatMap(s -> s.getLines().stream())
                .mapToLong(l -> l.netCents() * l.getReturnedQuantity() / l.getQuantity()).sum();
        long net = gross - discount - refundedNet;
        List<TopItem> top = items.values().stream()
                .filter(i -> i.units() > 0)
                .sorted(Comparator.comparingLong(TopItem::revenueCents).reversed())
                .limit(10)
                .toList();
        return new DailyReport(date, day.size(), gross, discount, tax, refunds, net, cogs, net - cogs, tenders, top);
    }
}
