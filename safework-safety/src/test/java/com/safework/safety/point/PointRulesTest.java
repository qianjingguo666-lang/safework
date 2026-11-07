package com.safework.safety.point;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PointRulesTest {

    @Test
    void reportPointsByLevel() {
        assertEquals(10, PointRules.reportPoints("GENERAL"));
        assertEquals(50, PointRules.reportPoints("MAJOR"));
        assertEquals(10, PointRules.reportPoints(null));
    }

    @Test
    void rectifyOnTimeOnly() {
        LocalDateTime deadline = LocalDateTime.of(2026, 11, 10, 18, 0);
        assertEquals(5, PointRules.rectifyPoints(deadline, deadline));
        assertEquals(5, PointRules.rectifyPoints(deadline.minusDays(1), deadline));
        assertEquals(0, PointRules.rectifyPoints(deadline.plusMinutes(1), deadline));
        assertEquals(0, PointRules.rectifyPoints(null, deadline));
    }

    @Test
    void adjust() {
        assertNull(PointRules.checkAdjust(20, "合理化建议", 0));
        assertNull(PointRules.checkAdjust(-10, "违章", 10));
        assertNotNull(PointRules.checkAdjust(-11, "违章", 10));
        assertNotNull(PointRules.checkAdjust(0, "x", 10));
        assertNotNull(PointRules.checkAdjust(201, "x", 0));
        assertNotNull(PointRules.checkAdjust(5, " ", 0));
    }

    @Test
    void redeem() {
        assertNull(PointRules.checkRedeem(100, 100, 1, true));
        assertNotNull(PointRules.checkRedeem(99, 100, 1, true));
        assertNotNull(PointRules.checkRedeem(100, 100, 0, true));
        assertNotNull(PointRules.checkRedeem(100, 100, 1, false));
    }

    @Test
    void ranges() {
        LocalDate[] m = PointRules.range(PointRules.MONTH, 2026, 12);
        assertEquals(LocalDate.of(2026, 12, 1), m[0]);
        assertEquals(LocalDate.of(2027, 1, 1), m[1]);
        LocalDate[] y = PointRules.range(PointRules.YEAR, 2026, 5);
        assertEquals(LocalDate.of(2026, 1, 1), y[0]);
        assertEquals(LocalDate.of(2027, 1, 1), y[1]);
    }

    @Test
    void rankWithTies() {
        Map<Long, Integer> p = new LinkedHashMap<>();
        p.put(1L, 30);
        p.put(2L, 50);
        p.put(3L, 30);
        p.put(4L, 10);
        p.put(5L, 0);
        List<PointRules.Ranked> r = PointRules.rank(p);
        assertEquals(4, r.size());
        assertEquals(2L, r.get(0).userId());
        assertEquals(1, r.get(0).rank());
        assertEquals(2, r.get(1).rank());
        assertEquals(2, r.get(2).rank());
        assertEquals(4, r.get(3).rank());
    }
}
