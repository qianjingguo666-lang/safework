package com.safework.safety.check;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Test
    void dueDate() {
        assertEquals(TODAY, CheckRules.due(CheckRules.DAILY, TODAY, null));
        assertEquals(TODAY.plusDays(6), CheckRules.due(CheckRules.WEEKLY, TODAY, null));
        assertEquals(LocalDate.of(2026, 11, 30), CheckRules.due(CheckRules.MONTHLY, LocalDate.of(2026, 11, 1), null));
        // 1 月 31 日开始的月度检查，截止 2 月 27 日（下个周期从 2 月 28 日开始）
        assertEquals(LocalDate.of(2027, 2, 27), CheckRules.due(CheckRules.MONTHLY, LocalDate.of(2027, 1, 31), null));
        assertEquals(TODAY.plusDays(6), CheckRules.due(CheckRules.ONCE, TODAY, null));
        assertEquals(TODAY.plusDays(14), CheckRules.due(CheckRules.ONCE, TODAY, TODAY.plusDays(14)));
        // 不能超过计划结束日期
        assertEquals(TODAY.plusDays(2), CheckRules.due(CheckRules.WEEKLY, TODAY, TODAY.plusDays(2)));
    }

    @Test
    void nextPeriod() {
        assertEquals(TODAY.plusDays(1), CheckRules.next(CheckRules.DAILY, TODAY));
        assertEquals(TODAY.plusWeeks(1), CheckRules.next(CheckRules.WEEKLY, TODAY));
        assertEquals(TODAY.plusMonths(1), CheckRules.next(CheckRules.MONTHLY, TODAY));
        assertNull(CheckRules.next(CheckRules.ONCE, TODAY));
        boolean thrown = false;
        try {
            CheckRules.next("YEARLY", TODAY);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    void periodToGenerate() {
        // 还没到时间
        assertNull(CheckRules.periodToGenerate(CheckRules.DAILY, TODAY.plusDays(1), null, TODAY));
        assertNull(CheckRules.periodToGenerate(CheckRules.DAILY, null, null, TODAY));
        // 正好今天
        assertEquals(TODAY, CheckRules.periodToGenerate(CheckRules.DAILY, TODAY, null, TODAY));
        // 停机 5 天：只生成今天的，不补前面 5 天
        assertEquals(TODAY, CheckRules.periodToGenerate(CheckRules.DAILY, TODAY.minusDays(5), null, TODAY));
        // 每周计划从 10 天前开始：今天落在第二周（3 天前开始的那一周）
        assertEquals(TODAY.minusDays(3), CheckRules.periodToGenerate(CheckRules.WEEKLY, TODAY.minusDays(10), null, TODAY));
        // 一次性：开始日期过了也生成（只有一次）
        assertEquals(TODAY.minusDays(2), CheckRules.periodToGenerate(CheckRules.ONCE, TODAY.minusDays(2), null, TODAY));
        // 计划已经结束
        assertNull(CheckRules.periodToGenerate(CheckRules.DAILY, TODAY.minusDays(5), TODAY.minusDays(1), TODAY));
        assertEquals(TODAY, CheckRules.periodToGenerate(CheckRules.DAILY, TODAY.minusDays(5), TODAY, TODAY));
    }

    @Test
    void state() {
        assertEquals(CheckRules.DONE, CheckRules.state(CheckRules.DONE, TODAY.minusDays(3), TODAY));
        assertEquals(CheckRules.PENDING, CheckRules.state(CheckRules.PENDING, TODAY, TODAY));
        assertEquals(CheckRules.OVERDUE, CheckRules.state(CheckRules.PENDING, TODAY.minusDays(1), TODAY));
        assertEquals(LocalDate.of(2026, 11, 2), CheckRules.monday(TODAY));
    }

    @Test
    void items() {
        assertNotNull(CheckRules.checkItems(List.of()));
        assertNull(CheckRules.checkItems(List.of(new CheckItem("液位正常", null, "设备设施"), new CheckItem("消防通道畅通", "目视", null))));
        assertNotNull(CheckRules.checkItems(List.of(new CheckItem(" ", null, null))));
        assertNotNull(CheckRules.checkItems(List.of(new CheckItem("液位正常", null, "外星类别"))));
        assertNotNull(CheckRules.checkItems(List.of(new CheckItem("液位正常", null, null), new CheckItem("液位正常 ", null, null))));
    }

    @Test
    void results() {
        assertNull(CheckRules.checkResults(2, List.of(true, true), null, null));
        // 漏查一项
        assertNotNull(CheckRules.checkResults(3, List.of(true, true), null, null));
        assertNotNull(CheckRules.checkResults(2, Arrays.asList(true, null), null, null));
        // 不合格：不转隐患又不写说明 → 不行
        assertNotNull(CheckRules.checkResults(2, List.of(true, false), List.of(false, false), Arrays.asList(null, " ")));
        // 写了现场处理情况可以
        assertNull(CheckRules.checkResults(2, List.of(true, false), List.of(false, false), Arrays.asList(null, "已当场关闭阀门")));
        // 转隐患可以不写说明
        assertNull(CheckRules.checkResults(2, List.of(true, false), List.of(false, true), null));
    }
}
