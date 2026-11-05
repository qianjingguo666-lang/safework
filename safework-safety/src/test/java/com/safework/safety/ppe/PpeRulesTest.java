package com.safework.safety.ppe;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void expireAndState() {
        // 安全帽 30 个月
        assertEquals(LocalDate.of(2029, 5, 5), PpeRules.expire(TODAY, 30));
        // 8 月 31 日发放、6 个月期限 → 2 月最后一天
        assertEquals(LocalDate.of(2027, 2, 28), PpeRules.expire(LocalDate.of(2026, 8, 31), 6));
        assertEquals(PpeRules.MISSING, PpeRules.state(null, TODAY));
        assertEquals(PpeRules.EXPIRED, PpeRules.state(TODAY.minusDays(1), TODAY));
        assertEquals(PpeRules.DUE_SOON, PpeRules.state(TODAY, TODAY));
        assertEquals(PpeRules.DUE_SOON, PpeRules.state(TODAY.plusDays(30), TODAY));
        assertEquals(PpeRules.OK, PpeRules.state(TODAY.plusDays(31), TODAY));
        assertTrue(PpeRules.needIssue(PpeRules.MISSING));
        assertTrue(PpeRules.needIssue(PpeRules.EXPIRED));
        assertFalse(PpeRules.needIssue(PpeRules.DUE_SOON));
    }

    @Test
    void remind() {
        assertTrue(PpeRules.shouldRemind(TODAY.plusDays(7), TODAY));
        assertTrue(PpeRules.shouldRemind(TODAY, TODAY));
        assertFalse(PpeRules.shouldRemind(TODAY.plusDays(6), TODAY));
        // 过期第 1 天、第 8 天提醒，中间不提醒
        assertTrue(PpeRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertFalse(PpeRules.shouldRemind(TODAY.minusDays(2), TODAY));
        assertTrue(PpeRules.shouldRemind(TODAY.minusDays(8), TODAY));
        assertFalse(PpeRules.shouldRemind(null, TODAY));
    }

    @Test
    void months() {
        assertNull(PpeRules.checkMonths(30));
        assertNotNull(PpeRules.checkMonths(0));
        assertNotNull(PpeRules.checkMonths(121));
        assertNotNull(PpeRules.checkMonths(null));
    }

    @Test
    void shortage() {
        Map<Long, Integer> need = new LinkedHashMap<>();
        need.put(1L, 2);
        need.put(2L, 6);
        Map<Long, Integer> stock = Map.of(1L, 5, 2L, 3);
        List<String> s = PpeRules.shortage(need, stock, Map.of(1L, "安全帽", 2L, "滤毒罐"));
        assertEquals(List.of("滤毒罐 需要 6，库存 3"), s);
        assertTrue(PpeRules.shortage(Map.of(1L, 5), stock, Map.of()).isEmpty());
        // 没有库存记录当 0 算
        assertEquals(1, PpeRules.shortage(Map.of(9L, 1), stock, Map.of()).size());
    }
}
