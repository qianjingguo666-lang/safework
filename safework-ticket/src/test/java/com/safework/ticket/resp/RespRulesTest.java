package com.safework.ticket.resp;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RespRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void duties() {
        assertEquals(List.of("遵守安全规程", "正确佩戴劳保用品"), RespRules.clean(Arrays.asList(" 1. 遵守安全规程", "", null, "2、正确佩戴劳保用品 ")));
        assertNull(RespRules.checkDuties(List.of("遵守安全规程")));
        assertNotNull(RespRules.checkDuties(List.of(" ", "")));
        assertNotNull(RespRules.checkDuties(List.of("1. 遵守安全规程", "遵守安全规程")));
        assertNotNull(RespRules.checkDuties(List.of("长".repeat(201))));
        // 多个岗位合并去重
        assertEquals(List.of("A", "B", "C"), RespRules.merge(List.of(List.of("A", "B"), List.of("1. B", "C"))));
    }

    @Test
    void grade() {
        assertEquals("优秀", RespRules.grade(90));
        assertEquals("良好", RespRules.grade(89));
        assertEquals("良好", RespRules.grade(80));
        assertEquals("合格", RespRules.grade(60));
        assertEquals("不合格", RespRules.grade(59));
        boolean thrown = false;
        try {
            RespRules.grade(101);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    void yearAndLate() {
        assertNull(RespRules.checkYear(2027, TODAY));
        assertNull(RespRules.checkYear(2025, TODAY));
        assertNotNull(RespRules.checkYear(2024, TODAY));
        assertFalse(RespRules.late(TODAY.atTime(23, 59), TODAY));
        assertTrue(RespRules.late(TODAY.plusDays(1).atTime(8, 0), TODAY));
        assertFalse(RespRules.late(null, TODAY));
    }

    @Test
    void remind() {
        assertTrue(RespRules.shouldRemind(TODAY.plusDays(3), TODAY));
        assertFalse(RespRules.shouldRemind(TODAY.plusDays(2), TODAY));
        assertTrue(RespRules.shouldRemind(TODAY.plusDays(1), TODAY));
        assertTrue(RespRules.shouldRemind(TODAY, TODAY));
        assertTrue(RespRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertFalse(RespRules.shouldRemind(TODAY.minusDays(2), TODAY));
        assertTrue(RespRules.shouldRemind(TODAY.minusDays(4), TODAY));
        assertEquals(67, RespRules.percent(2, 3));
        assertNull(RespRules.percent(0, 0));
    }
}
