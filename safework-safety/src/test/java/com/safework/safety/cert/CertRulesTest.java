package com.safework.safety.cert;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    @Test
    void validUntilTakesEarlierDate() {
        assertEquals(LocalDate.of(2027, 5, 1), CertRules.validUntil(LocalDate.of(2030, 1, 1), LocalDate.of(2027, 5, 1)));
        assertEquals(LocalDate.of(2027, 5, 1), CertRules.validUntil(LocalDate.of(2027, 5, 1), null));
        assertEquals(LocalDate.of(2027, 5, 1), CertRules.validUntil(null, LocalDate.of(2027, 5, 1)));
        assertNull(CertRules.validUntil(null, null));
        assertEquals("复审", CertRules.limitName(LocalDate.of(2030, 1, 1), LocalDate.of(2027, 5, 1)));
        assertEquals("有效期", CertRules.limitName(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 5, 1)));
    }

    @Test
    void state() {
        assertEquals(CertRules.VALID, CertRules.state(null, TODAY));
        assertEquals(CertRules.EXPIRED, CertRules.state(TODAY.minusDays(1), TODAY));
        assertEquals(CertRules.EXPIRING, CertRules.state(TODAY, TODAY));
        assertEquals(CertRules.EXPIRING, CertRules.state(TODAY.plusDays(30), TODAY));
        assertEquals(CertRules.VALID, CertRules.state(TODAY.plusDays(31), TODAY));
    }

    @Test
    void remindOnlyOnMilestones() {
        assertTrue(CertRules.shouldRemind(TODAY.plusDays(30), TODAY));
        assertTrue(CertRules.shouldRemind(TODAY.plusDays(7), TODAY));
        assertTrue(CertRules.shouldRemind(TODAY, TODAY));
        assertFalse(CertRules.shouldRemind(TODAY.plusDays(29), TODAY));
        assertFalse(CertRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertFalse(CertRules.shouldRemind(null, TODAY));
    }

    @Test
    void problems() {
        LocalDate workEnd = TODAY.plusDays(2);
        // 合格
        assertTrue(CertRules.problems(List.of("WELDING"),
                List.of(new CertRules.Held("WELDING", "T1", TODAY.plusYears(3), null)), workEnd, TODAY).isEmpty());
        // 长期有效
        assertTrue(CertRules.problems(List.of("GUARDIAN"),
                List.of(new CertRules.Held("GUARDIAN", "G1", null, null)), workEnd, TODAY).isEmpty());
        // 没有证
        List<String> p = CertRules.problems(List.of("WELDING", "HEIGHT"),
                List.of(new CertRules.Held("WELDING", "T1", TODAY.plusYears(3), null)), workEnd, TODAY);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("没有") && p.get(0).contains("高处"));
        // 过期
        p = CertRules.problems(List.of("WELDING"), List.of(new CertRules.Held("WELDING", "T1", TODAY.minusDays(1), null)), workEnd, TODAY);
        assertTrue(p.get(0).contains("已过期"));
        // 复审逾期
        p = CertRules.problems(List.of("WELDING"), List.of(new CertRules.Held("WELDING", "T1", TODAY.plusYears(3), TODAY.minusDays(3))), workEnd, TODAY);
        assertTrue(p.get(0).contains("复审已逾期"));
        // 作业期间到期
        p = CertRules.problems(List.of("WELDING"), List.of(new CertRules.Held("WELDING", "T1", TODAY.plusDays(1), null)), workEnd, TODAY);
        assertTrue(p.get(0).contains("早于作业结束时间"));
        // 旧证过期、新证有效：取新证
        assertTrue(CertRules.problems(List.of("WELDING"), List.of(
                new CertRules.Held("WELDING", "OLD", TODAY.minusYears(1), null),
                new CertRules.Held("WELDING", "NEW", TODAY.plusYears(6), TODAY.plusYears(3))), workEnd, TODAY).isEmpty());
    }
}
