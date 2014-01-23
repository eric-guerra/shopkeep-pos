package com.ericguerra.shopkeep.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceCalculatorTest {
    private final PriceCalculator calc = new PriceCalculator(new BigDecimal("0.0825"));

    @Test
    void taxesOnlyTaxableLinesAfterDiscount() {
        var totals = calc.price(List.of(
                new PriceCalculator.LineInput(1450, 2, 10, true),    // 2900 gross, 290 off -> 2610 net, tax 215.325 -> 215
                new PriceCalculator.LineInput(1199, 1, 0, false)),   // groceries-style untaxed item
                0);
        assertEquals(4099, totals.subtotalCents());
        assertEquals(290, totals.discountCents());
        assertEquals(215, totals.taxCents());
        assertEquals(4099 - 290 + 215, totals.totalCents());
        assertEquals(0, totals.lines().get(1).taxCents());
    }

    @Test
    void orderDiscountIsAllocatedExactlyAcrossLines() {
        var totals = calc.price(List.of(
                new PriceCalculator.LineInput(1000, 1, 0, true),
                new PriceCalculator.LineInput(1000, 1, 0, true),
                new PriceCalculator.LineInput(1000, 1, 0, true)), 100);
        long allocated = totals.lines().stream().mapToLong(PriceCalculator.LineResult::discountCents).sum();
        assertEquals(100, allocated, "rounding remainder must not be lost");
        assertEquals(100, totals.discountCents());
    }

    @Test
    void orderDiscountCannotExceedNet() {
        var totals = calc.price(List.of(new PriceCalculator.LineInput(500, 1, 0, false)), 10_000);
        assertEquals(500, totals.discountCents());
        assertEquals(0, totals.totalCents());
    }

    @Test
    void rejectsInvalidLines() {
        assertThrows(IllegalArgumentException.class, () -> new PriceCalculator.LineInput(100, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new PriceCalculator.LineInput(100, 1, 101, true));
        assertThrows(IllegalArgumentException.class, () -> calc.price(List.of(), -1));
    }

    @Test
    void changeCalculation() {
        assertEquals(301, PriceCalculator.change(1699, 2000));
        assertEquals(0, PriceCalculator.change(1699, 1699));
        assertThrows(IllegalArgumentException.class, () -> PriceCalculator.change(1699, 1000));
    }
}
