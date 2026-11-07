package com.safework.safety.injury;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryRulesTest {

    private static final LocalDate INJURY = LocalDate.of(2026, 10, 10);

    @Test
    void applyDeadlineIs30DaysAfterInjury() {
        InjuryRules.Deadline d = InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 2));
        assertEquals(LocalDate.of(2026, 11, 9), d.dueDate());
        assertEquals(7, d.daysLeft());
        assertEquals(InjuryRules.SOON, d.state());
        assertEquals(InjuryRules.OK, InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 1)).state());
        assertEquals(InjuryRules.SOON, InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 9)).state());
        assertEquals(InjuryRules.OVERDUE, InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 10)).state());
        assertEquals(InjuryRules.DONE, InjuryRules.applyDeadline(INJURY, InjuryRules.APPLIED, LocalDate.of(2026, 11, 20)).state());
    }

    @Test
    void remindOn7310AndOverdue() {
        assertTrue(InjuryRules.shouldRemind(InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 2))));
        assertFalse(InjuryRules.shouldRemind(InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 3))));
        assertTrue(InjuryRules.shouldRemind(InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 9))));
        assertTrue(InjuryRules.shouldRemind(InjuryRules.applyDeadline(INJURY, InjuryRules.REPORTED, LocalDate.of(2026, 11, 15))));
        assertFalse(InjuryRules.shouldRemind(InjuryRules.applyDeadline(INJURY, InjuryRules.APPLIED, LocalDate.of(2026, 11, 15))));
    }

    @Test
    void leavePeriod() {
        LocalDate s = INJURY;
        assertNull(InjuryRules.checkLeave(s, s.plusMonths(12), false, INJURY));
        assertNotNull(InjuryRules.checkLeave(s, s.plusMonths(12).plusDays(1), false, INJURY));
        assertNull(InjuryRules.checkLeave(s, s.plusMonths(24), true, INJURY));
        assertNotNull(InjuryRules.checkLeave(s, s.plusMonths(24).plusDays(1), true, INJURY));
        assertNotNull(InjuryRules.checkLeave(s.minusDays(1), s, false, INJURY));
        assertNotNull(InjuryRules.checkLeave(s, s.minusDays(1), false, INJURY));
    }

    @Test
    void lumpSumByGrade() {
        assertEquals(27, InjuryRules.lumpSumMonths(1));
        assertEquals(7, InjuryRules.lumpSumMonths(10));
        assertEquals(0, InjuryRules.lumpSumMonths(0));
        assertEquals(new BigDecimal("29400.00"), InjuryRules.lumpSum(10, new BigDecimal("4200")));
        assertNull(InjuryRules.lumpSum(10, null));
        assertThrows(IllegalArgumentException.class, () -> InjuryRules.lumpSumMonths(11));
        assertEquals("十级伤残", InjuryRules.gradeLabel(10));
        assertEquals("未达到伤残等级", InjuryRules.gradeLabel(0));
    }

    @Test
    void actions() {
        assertTrue(InjuryRules.Action.APPLY.canFire(InjuryRules.REPORTED));
        assertFalse(InjuryRules.Action.APPRAISE.canFire(InjuryRules.APPLIED));
        assertTrue(InjuryRules.Action.CLOSE.canFire(InjuryRules.NOT_RECOGNIZED));
        assertFalse(InjuryRules.Action.CLOSE.canFire(InjuryRules.REPORTED));
    }
}
