package com.safework.safety.drill;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrillRulesTest {

    private static List<DrillRules.Score> full() {
        List<DrillRules.Score> l = new ArrayList<>();
        DrillRules.SCORE_ITEMS.forEach(i -> l.add(new DrillRules.Score(i.code(), BigDecimal.valueOf(i.max()))));
        return l;
    }

    @Test
    void itemsAddUpTo100() {
        assertEquals(100, DrillRules.SCORE_ITEMS.stream().mapToInt(DrillRules.ScoreItem::max).sum());
    }

    @Test
    void checkScores() {
        assertNull(DrillRules.checkScores(full()));
        List<DrillRules.Score> l = full();
        l.remove(0);
        assertNotNull(DrillRules.checkScores(l), "少一项");
        l = full();
        l.set(0, new DrillRules.Score("REPORT", BigDecimal.valueOf(11)));
        assertNotNull(DrillRules.checkScores(l), "超过满分");
        l.set(0, new DrillRules.Score("REPORT", new BigDecimal("-1")));
        assertNotNull(DrillRules.checkScores(l), "负数");
        l.set(0, new DrillRules.Score("REPORT", new BigDecimal("8.3")));
        assertNotNull(DrillRules.checkScores(l), "最小 0.5");
        l.set(0, new DrillRules.Score("REPORT", new BigDecimal("8.5")));
        assertNull(DrillRules.checkScores(l));
        l.set(1, new DrillRules.Score("REPORT", BigDecimal.ONE));
        assertNotNull(DrillRules.checkScores(l), "重复项目");
        l = full();
        for (int i = 0; i < 5; i++) {
            l.set(i, new DrillRules.Score(l.get(i).code(), null));
        }
        assertNotNull(DrillRules.checkScores(l), "只剩 3 个适用");
        l.set(0, new DrillRules.Score("REPORT", BigDecimal.TEN));
        assertNull(DrillRules.checkScores(l), "4 个适用可以");
    }

    @Test
    void resultScalesToApplicable() {
        assertEquals(new BigDecimal("100.0"), DrillRules.result(full()).total());
        List<DrillRules.Score> l = full();
        // 桌面推演：医疗救护、后期处置不适用，现场处置得 15（满分 20）
        l.set(6, new DrillRules.Score("MEDICAL", null));
        l.set(7, new DrillRules.Score("RECOVERY", null));
        l.set(3, new DrillRules.Score("DISPOSAL", BigDecimal.valueOf(15)));
        DrillRules.Result r = DrillRules.result(l);
        assertEquals(80, r.applicableMax());
        assertEquals(new BigDecimal("93.8"), r.total());
        assertEquals("优秀", r.grade());
    }

    @Test
    void grades() {
        assertEquals("优秀", DrillRules.grade(new BigDecimal("90.0")));
        assertEquals("良好", DrillRules.grade(new BigDecimal("89.9")));
        assertEquals("良好", DrillRules.grade(new BigDecimal("75")));
        assertEquals("合格", DrillRules.grade(new BigDecimal("74.9")));
        assertEquals("合格", DrillRules.grade(new BigDecimal("60")));
        assertEquals("不合格", DrillRules.grade(new BigDecimal("59.9")));
    }

    @Test
    void yearRequirement() {
        assertEquals(2, DrillRules.requiredPerYear("ONSITE"));
        assertEquals(1, DrillRules.requiredPerYear("SPECIAL"));
        assertEquals(DrillRules.YEAR_DONE, DrillRules.yearState("SPECIAL", 1, 0));
        assertEquals(DrillRules.YEAR_PLANNED, DrillRules.yearState("ONSITE", 1, 1));
        assertEquals(DrillRules.YEAR_MISSING, DrillRules.yearState("ONSITE", 1, 0));
        assertEquals(DrillRules.YEAR_MISSING, DrillRules.yearState("COMPREHENSIVE", 0, 0));
    }

    @Test
    void signWindowAndReminders() {
        LocalDateTime plan = LocalDateTime.of(2026, 11, 10, 9, 0);
        assertFalse(DrillRules.canSign("PLANNED", plan, plan.minusHours(2).minusMinutes(1)));
        assertTrue(DrillRules.canSign("PLANNED", plan, plan.minusHours(2)));
        assertTrue(DrillRules.canSign("PLANNED", plan, LocalDateTime.of(2026, 11, 10, 23, 59)));
        assertFalse(DrillRules.canSign("PLANNED", plan, LocalDateTime.of(2026, 11, 11, 0, 0)));
        assertFalse(DrillRules.canSign("DONE", plan, plan));
        // 凌晨 1 点的演练，前一天 23 点不能签（不是同一天）
        LocalDateTime early = LocalDateTime.of(2026, 11, 10, 1, 0);
        assertFalse(DrillRules.canSign("PLANNED", early, LocalDateTime.of(2026, 11, 9, 23, 30)));

        assertTrue(DrillRules.remindParticipants(plan, LocalDate.of(2026, 11, 7)));
        assertFalse(DrillRules.remindParticipants(plan, LocalDate.of(2026, 11, 8)));
        assertTrue(DrillRules.remindParticipants(plan, LocalDate.of(2026, 11, 9)));
        assertTrue(DrillRules.remindParticipants(plan, LocalDate.of(2026, 11, 10)));
        assertFalse(DrillRules.remindParticipants(plan, LocalDate.of(2026, 11, 11)));

        assertFalse(DrillRules.recordLate("PLANNED", plan, LocalDate.of(2026, 11, 10)));
        assertTrue(DrillRules.recordLate("PLANNED", plan, LocalDate.of(2026, 11, 11)));
        assertFalse(DrillRules.recordLate("DONE", plan, LocalDate.of(2026, 11, 20)));
        assertFalse(DrillRules.evaluateLate("DONE", plan, LocalDate.of(2026, 11, 12)));
        assertTrue(DrillRules.evaluateLate("DONE", plan, LocalDate.of(2026, 11, 13)));
        assertFalse(DrillRules.evaluateLate("EVALUATED", plan, LocalDate.of(2026, 11, 20)));
    }
}
