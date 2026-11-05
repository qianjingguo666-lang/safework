package com.safework.safety.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostRulesTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void accrueFirstTierOnly() {
        // 800 万 × 4.5% = 36 万
        CostRules.Accrual a = CostRules.accrue(d("800"));
        assertEquals(d("360000.00"), a.total());
        assertEquals(1, a.lines().size());
    }

    @Test
    void accrueIsProgressive() {
        // 1000 万 × 4.5% + 7000 万 × 2.25% = 45 万 + 157.5 万 = 202.5 万
        CostRules.Accrual a = CostRules.accrue(d("8000"));
        assertEquals(d("2025000.00"), a.total());
        assertEquals(2, a.lines().size());
        assertEquals(d("1575000.00"), a.lines().get(1).amount());
    }

    @Test
    void accrueAllTiers() {
        // 45 万 + 9000 万 × 2.25% (202.5 万) + 9 亿 × 0.55% (495 万) + 10 亿 × 0.2% (200 万) = 942.5 万
        CostRules.Accrual a = CostRules.accrue(d("200000"));
        assertEquals(d("9425000.00"), a.total());
        assertEquals(4, a.lines().size());
    }

    @Test
    void accrueBoundaryAndZero() {
        assertEquals(d("450000.00"), CostRules.accrue(d("1000")).total());
        assertEquals(1, CostRules.accrue(d("1000")).lines().size());
        assertEquals(d("0.00"), CostRules.accrue(BigDecimal.ZERO).total());
        assertEquals(d("0.00"), CostRules.accrue(null).total());
    }

    @Test
    void monthlyAverage() {
        assertEquals(d("1012500.00"), CostRules.accruedByMonth(d("2025000.00"), 6));
        assertEquals(d("2025000.00"), CostRules.accruedByMonth(d("2025000.00"), 15));
    }

    @Test
    void usageRate() {
        assertEquals(25.0, CostRules.rate(d("50"), d("200")));
        assertNull(CostRules.rate(d("50"), BigDecimal.ZERO));
    }

    @Test
    void categoryState() {
        assertEquals(CostRules.OVER, CostRules.categoryState(d("100"), d("100.01")));
        assertEquals(CostRules.NORMAL, CostRules.categoryState(d("100"), d("100")));
        assertEquals(CostRules.UNPLANNED, CostRules.categoryState(BigDecimal.ZERO, d("1")));
        assertEquals(CostRules.NORMAL, CostRules.categoryState(null, BigDecimal.ZERO));
    }

    @Test
    void timeProgressAndLag() {
        assertEquals(100.0, CostRules.timeProgress(2025, LocalDate.of(2026, 3, 1)));
        assertEquals(0.0, CostRules.timeProgress(2027, LocalDate.of(2026, 3, 1)));
        double p = CostRules.timeProgress(2026, LocalDate.of(2026, 7, 2));
        assertTrue(p > 49 && p < 51);
        assertTrue(CostRules.lagging(20.0, p));
        assertFalse(CostRules.lagging(30.0, p));
        assertFalse(CostRules.lagging(1.0, 10));
        assertFalse(CostRules.lagging(null, 80));
    }

    @Test
    void pauseAndBudget() {
        assertTrue(CostRules.canPause(d("300"), d("100")));
        assertFalse(CostRules.canPause(d("299"), d("100")));
        assertFalse(CostRules.canPause(d("300"), null));
        assertNotNull(CostRules.budgetWarning(d("101"), d("100")));
        assertNull(CostRules.budgetWarning(d("100"), d("100")));
    }

    @Test
    void expenseDate() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertNull(CostRules.checkExpenseDate(today, today));
        assertNotNull(CostRules.checkExpenseDate(today.plusDays(1), today));
        assertEquals(10, CostRules.CATEGORIES.size());
    }
}
