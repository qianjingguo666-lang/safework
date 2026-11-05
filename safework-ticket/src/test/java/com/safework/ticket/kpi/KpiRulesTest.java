package com.safework.ticket.kpi;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KpiRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void defaults() {
        assertEquals(100, KpiRules.METRICS.stream().mapToInt(KpiRules.Metric::defaultWeight).sum());
        assertEquals(8, KpiRules.BY_CODE.size());
    }

    @Test
    void lightAndScore() {
        assertEquals(KpiRules.GREEN, KpiRules.light(KpiRules.HIGHER, 95.0, 95));
        assertEquals(KpiRules.YELLOW, KpiRules.light(KpiRules.HIGHER, 85.5, 95));
        assertEquals(KpiRules.RED, KpiRules.light(KpiRules.HIGHER, 85.4, 95));
        assertEquals(KpiRules.NO_DATA, KpiRules.light(KpiRules.HIGHER, null, 95));
        assertEquals(KpiRules.GREEN, KpiRules.light(KpiRules.LOWER, 0.0, 0));
        assertEquals(KpiRules.RED, KpiRules.light(KpiRules.LOWER, 1.0, 0));
        assertEquals(20.0, KpiRules.score(KpiRules.HIGHER, 100.0, 95, 20));
        assertEquals(18.0, KpiRules.score(KpiRules.HIGHER, 85.5, 95, 20));
        assertEquals(0.0, KpiRules.score(KpiRules.LOWER, 1.0, 0, 15));
        assertEquals(15.0, KpiRules.score(KpiRules.LOWER, 0.0, 0, 15));
        assertNull(KpiRules.score(KpiRules.HIGHER, null, 95, 20));
        // 没数据的不参与：20 + 0 分，满分 35 → 57.1
        assertEquals(57.1, KpiRules.total(Arrays.asList(20.0, null, 0.0), List.of(20, 10, 15)));
        assertNull(KpiRules.total(Arrays.asList(null, null), List.of(50, 50)));
    }

    @Test
    void rate() {
        assertEquals(66.7, KpiRules.rate(2, 3));
        assertNull(KpiRules.rate(0, 0));
        assertEquals(50.0, KpiRules.ratio(List.of(1, 2, 3, 4, 5), x -> x > 1, x -> x % 2 == 0));
    }

    @Test
    void targets() {
        Map<String, Double> t = new HashMap<>();
        Map<String, Integer> w = new HashMap<>();
        KpiRules.METRICS.forEach(m -> {
            t.put(m.code(), m.defaultTarget());
            w.put(m.code(), m.defaultWeight());
        });
        assertNull(KpiRules.checkTargets(t, w));
        w.put("ACCIDENT", 16);
        assertTrue(KpiRules.checkTargets(t, w).contains("101"));
        w.put("ACCIDENT", 15);
        t.put("HAZARD_ONTIME", 101.0);
        assertNotNull(KpiRules.checkTargets(t, w));
        t.remove("HAZARD_ONTIME");
        assertNotNull(KpiRules.checkTargets(t, w));
    }

    @Test
    void ranges() {
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 6)}, KpiRules.yearRange(2026, TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)}, KpiRules.yearRange(2025, TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6)}, KpiRules.monthRange(2026, 11, TODAY));
        assertArrayEquals(new LocalDate[]{LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)}, KpiRules.monthRange(2026, 2, TODAY));
        assertNull(KpiRules.monthRange(2026, 12, TODAY));
        assertEquals(5, KpiRules.days(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6)).size());
    }
}
