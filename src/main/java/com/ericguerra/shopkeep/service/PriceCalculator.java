package com.ericguerra.shopkeep.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure pricing math, all in integer cents.
 * <ul>
 *   <li>Line discounts are percentages applied per line.</li>
 *   <li>An order-level discount (fixed cents) is spread across lines in proportion to their net,
 *       with the rounding remainder on the largest line, so line totals always sum exactly.</li>
 *   <li>Tax is computed per taxable line after discounts (half-up), which is how receipts itemize it.</li>
 * </ul>
 */
public final class PriceCalculator {
    public record LineInput(long unitPriceCents, int quantity, int discountPercent, boolean taxable) {
        public LineInput {
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (discountPercent < 0 || discountPercent > 100) throw new IllegalArgumentException("discount must be 0-100%");
            if (unitPriceCents < 0) throw new IllegalArgumentException("price must not be negative");
        }
    }

    public record LineResult(long grossCents, long discountCents, long taxCents) {
        public long netCents() {
            return grossCents - discountCents;
        }
    }

    public record Totals(List<LineResult> lines, long subtotalCents, long discountCents, long taxCents, long totalCents) {}

    private final BigDecimal taxRate;

    public PriceCalculator(BigDecimal taxRate) {
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("tax rate must be 0-1");
        this.taxRate = taxRate;
    }

    public Totals price(List<LineInput> inputs, long orderDiscountCents) {
        if (orderDiscountCents < 0) throw new IllegalArgumentException("order discount must not be negative");
        List<Long> gross = new ArrayList<>();
        List<Long> lineDisc = new ArrayList<>();
        long netSum = 0;
        for (LineInput in : inputs) {
            long g = in.unitPriceCents() * in.quantity();
            long d = percentOf(g, in.discountPercent());
            gross.add(g);
            lineDisc.add(d);
            netSum += g - d;
        }
        long orderDisc = Math.min(orderDiscountCents, netSum);

        // Allocate the order discount proportionally.
        long[] alloc = new long[inputs.size()];
        long allocated = 0;
        int largest = -1;
        for (int i = 0; i < inputs.size(); i++) {
            long net = gross.get(i) - lineDisc.get(i);
            alloc[i] = netSum == 0 ? 0 : orderDisc * net / netSum;
            allocated += alloc[i];
            if (largest < 0 || net > gross.get(largest) - lineDisc.get(largest)) largest = i;
        }
        if (largest >= 0) alloc[largest] += orderDisc - allocated;

        List<LineResult> lines = new ArrayList<>();
        long subtotal = 0, discount = 0, tax = 0;
        for (int i = 0; i < inputs.size(); i++) {
            long d = lineDisc.get(i) + alloc[i];
            long net = gross.get(i) - d;
            long t = inputs.get(i).taxable()
                    ? BigDecimal.valueOf(net).multiply(taxRate).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    : 0;
            lines.add(new LineResult(gross.get(i), d, t));
            subtotal += gross.get(i);
            discount += d;
            tax += t;
        }
        return new Totals(lines, subtotal, discount, tax, subtotal - discount + tax);
    }

    private static long percentOf(long cents, int percent) {
        return BigDecimal.valueOf(cents).multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Cash is rounded to the cent (no penny rounding); returns change or throws if insufficient. */
    public static long change(long totalCents, long tenderedCents) {
        if (tenderedCents < totalCents) throw new IllegalArgumentException("tendered amount is less than the total");
        return tenderedCents - totalCents;
    }
}
