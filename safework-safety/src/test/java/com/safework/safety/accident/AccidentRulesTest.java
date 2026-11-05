package com.safework.safety.accident;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccidentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void deadlineAndState() {
        assertEquals(30, AccidentRules.deadlineDays("GENERAL"));
        assertEquals(60, AccidentRules.deadlineDays("LARGER"));
        assertEquals(60, AccidentRules.deadlineDays("EXTRA"));
        assertEquals(LocalDate.of(2026, 12, 5), AccidentRules.defaultDeadline("GENERAL", TODAY));
        // 期限当天还不算超期
        assertEquals(AccidentRules.INVESTIGATING, AccidentRules.state(AccidentRules.INVESTIGATING, TODAY, TODAY));
        assertEquals(AccidentRules.OVERDUE, AccidentRules.state(AccidentRules.INVESTIGATING, TODAY.minusDays(1), TODAY));
        assertEquals(AccidentRules.CLOSED, AccidentRules.state(AccidentRules.CLOSED, TODAY.minusDays(9), TODAY));
        assertEquals(AccidentRules.OPEN, AccidentRules.measureState(AccidentRules.OPEN, TODAY, TODAY));
        assertEquals(AccidentRules.OVERDUE, AccidentRules.measureState(AccidentRules.OPEN, TODAY.minusDays(1), TODAY));
        assertEquals(AccidentRules.DONE, AccidentRules.measureState(AccidentRules.DONE, TODAY.minusDays(1), TODAY));
    }

    @Test
    void measureDeadline() {
        assertNotNull(AccidentRules.checkMeasureDeadline(null, TODAY));
        assertNotNull(AccidentRules.checkMeasureDeadline(TODAY.minusDays(1), TODAY));
        assertNull(AccidentRules.checkMeasureDeadline(TODAY, TODAY));
        assertNull(AccidentRules.checkMeasureDeadline(TODAY.plusDays(180), TODAY));
        assertNotNull(AccidentRules.checkMeasureDeadline(TODAY.plusDays(181), TODAY));
    }

    @Test
    void remind() {
        assertTrue(AccidentRules.shouldRemind(TODAY.plusDays(3), TODAY));
        assertFalse(AccidentRules.shouldRemind(TODAY.plusDays(2), TODAY));
        assertTrue(AccidentRules.shouldRemind(TODAY, TODAY));
        assertTrue(AccidentRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertFalse(AccidentRules.shouldRemind(TODAY.minusDays(2), TODAY));
        assertTrue(AccidentRules.shouldRemind(TODAY.minusDays(4), TODAY));
        assertFalse(AccidentRules.shouldWarnOverdue(TODAY, TODAY));
        assertTrue(AccidentRules.shouldWarnOverdue(TODAY.minusDays(1), TODAY));
        assertFalse(AccidentRules.shouldWarnOverdue(TODAY.minusDays(2), TODAY));
        assertTrue(AccidentRules.shouldWarnOverdue(TODAY.minusDays(8), TODAY));
    }

    @Test
    void fourNots() {
        String cause = "作业前未进行气体检测，盲目进入受限空间";
        List<AccidentRules.Check> none = AccidentRules.fourNots(null, "太短", 0, 0, 0, null, null);
        assertEquals(4, none.size());
        assertTrue(none.stream().noneMatch(AccidentRules.Check::ok));
        assertNotNull(AccidentRules.closeError(none));

        // 措施没做完不能结案
        List<AccidentRules.Check> half = AccidentRules.fourNots(cause, cause, 1, 2, 1, TODAY, 20);
        assertTrue(half.get(0).ok());
        assertTrue(half.get(1).ok());
        assertFalse(half.get(2).ok());
        assertTrue(half.get(3).ok());
        assertTrue(AccidentRules.closeError(half).contains("整改措施未落实不放过"));
        // 教育人数为 0 不算
        assertFalse(AccidentRules.fourNots(cause, cause, 1, 2, 2, TODAY, 0).get(3).ok());

        List<AccidentRules.Check> all = AccidentRules.fourNots(cause, cause, 2, 3, 3, TODAY, 35);
        assertTrue(all.stream().allMatch(AccidentRules.Check::ok));
        assertNull(AccidentRules.closeError(all));
    }

    @Test
    void caseText() {
        String t = AccidentRules.caseText("SG-1", "污水池中毒", "一般事故", "中毒窒息", "2026-10-01 10:00", null,
                "经过", "直接", "", "管理", List.of("措施一", "措施二"), "教训");
        assertTrue(t.startsWith("# 事故案例：污水池中毒"));
        assertTrue(t.contains("2. 措施二"));
        assertTrue(t.contains("地点：（无）"));
        assertFalse(t.contains("间接原因"));
    }
}
