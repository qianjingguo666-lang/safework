package com.safework.safety.health;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void cycleAndState() {
        // 多种危害因素取最短周期
        assertEquals(6, HealthRules.cycle(List.of(12, 6, 24)));
        assertEquals(12, HealthRules.cycle(List.of()));
        assertEquals(12, HealthRules.cycle(Arrays.asList(null, 0)));
        assertEquals(LocalDate.of(2027, 11, 5), HealthRules.nextExam(TODAY, 12));
        assertNull(HealthRules.nextExam(null, 12));
        assertEquals(HealthRules.NEVER, HealthRules.state(null, TODAY));
        assertEquals(HealthRules.OVERDUE, HealthRules.state(TODAY.minusDays(1), TODAY));
        assertEquals(HealthRules.DUE_SOON, HealthRules.state(TODAY.plusDays(30), TODAY));
        assertEquals(HealthRules.OK, HealthRules.state(TODAY.plusDays(31), TODAY));
    }

    @Test
    void types() {
        assertTrue(HealthRules.countsForCycle("PRE"));
        assertTrue(HealthRules.countsForCycle("ON"));
        assertFalse(HealthRules.countsForCycle("LEAVE"));
        assertFalse(HealthRules.countsForCycle("EMERGENCY"));
        assertTrue(HealthRules.isAbnormal("RECHECK"));
        assertTrue(HealthRules.isAbnormal("CONTRAINDICATION"));
        assertFalse(HealthRules.isAbnormal("NORMAL"));
        assertFalse(HealthRules.isAbnormal("OTHER"));
    }

    @Test
    void remind() {
        assertTrue(HealthRules.shouldRemind(TODAY.plusDays(30), TODAY));
        assertTrue(HealthRules.shouldRemind(TODAY.plusDays(7), TODAY));
        assertTrue(HealthRules.shouldRemind(TODAY, TODAY));
        assertFalse(HealthRules.shouldRemind(TODAY.plusDays(20), TODAY));
        assertTrue(HealthRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertFalse(HealthRules.shouldRemind(TODAY.minusDays(3), TODAY));
        assertTrue(HealthRules.shouldRemind(TODAY.minusDays(15), TODAY));
    }

    @Test
    void checkExam() {
        assertNull(HealthRules.checkExam("ON", "NORMAL", TODAY, null, TODAY));
        assertNotNull(HealthRules.checkExam("YEARLY", "NORMAL", TODAY, null, TODAY));
        assertNotNull(HealthRules.checkExam("ON", "GOOD", TODAY, null, TODAY));
        assertNotNull(HealthRules.checkExam("ON", "NORMAL", TODAY.plusDays(1), null, TODAY));
        assertNotNull(HealthRules.checkExam("ON", "NORMAL", TODAY.minusYears(4), null, TODAY));
        // 需要复查必须有复查日期，且在 90 天内
        assertNotNull(HealthRules.checkExam("ON", "RECHECK", TODAY, null, TODAY));
        assertNull(HealthRules.checkExam("ON", "RECHECK", TODAY, TODAY.plusDays(30), TODAY));
        assertNotNull(HealthRules.checkExam("ON", "RECHECK", TODAY, TODAY.plusDays(91), TODAY));
        assertNotNull(HealthRules.checkExam("ON", "RECHECK", TODAY, TODAY, TODAY));
        // 不是复查不能填复查日期
        assertNotNull(HealthRules.checkExam("ON", "NORMAL", TODAY, TODAY.plusDays(10), TODAY));
    }
}
