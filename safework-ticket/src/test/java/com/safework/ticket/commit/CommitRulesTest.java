package com.safework.ticket.commit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitRulesTest {

    private static List<CommitRules.Result> allOk(String aspect) {
        List<CommitRules.Result> l = new ArrayList<>();
        CommitRules.itemsOf(aspect).forEach(i -> l.add(new CommitRules.Result(i.code(), true, null, null)));
        return l;
    }

    @Test
    void everyAspectHasFourItems() {
        CommitRules.ASPECTS.keySet().forEach(a -> assertEquals(4, CommitRules.itemsOf(a).size(), a));
        assertEquals(16, CommitRules.ITEMS.stream().map(CommitRules.Item::code).distinct().count());
    }

    @Test
    void checkJudgment() {
        assertNull(CommitRules.checkJudgment("SAFETY", allOk("SAFETY")));
        assertNotNull(CommitRules.checkJudgment("BOSS", allOk("SAFETY")));
        List<CommitRules.Result> l = allOk("SAFETY");
        l.remove(0);
        assertNotNull(CommitRules.checkJudgment("SAFETY", l), "少一项");
        assertNotNull(CommitRules.checkJudgment("SAFETY", allOk("PRODUCTION")), "别的方面的项目");
        l = allOk("SAFETY");
        l.set(1, new CommitRules.Result("S1", true, null, null));
        assertNotNull(CommitRules.checkJudgment("SAFETY", l), "重复");
        l = allOk("SAFETY");
        l.set(0, new CommitRules.Result("S1", false, "有一张动火票气体检测超时", null));
        assertNotNull(CommitRules.checkJudgment("SAFETY", l), "异常没写措施");
        l.set(0, new CommitRules.Result("S1", false, "  ", "暂停作业重新检测"));
        assertNotNull(CommitRules.checkJudgment("SAFETY", l), "异常没写情况");
        l.set(0, new CommitRules.Result("S1", false, "有一张动火票气体检测超时", "暂停作业重新检测"));
        assertNull(CommitRules.checkJudgment("SAFETY", l));
        assertEquals(1, CommitRules.abnormal(l));
    }

    @Test
    void units() {
        assertNull(CommitRules.checkUnits(new CommitRules.Units(5, 3, 1, 1)));
        assertNull(CommitRules.checkUnits(new CommitRules.Units(1, 0, 0, 1)));
        assertNotNull(CommitRules.checkUnits(new CommitRules.Units(5, 3, 1, 0)), "对不上");
        assertNotNull(CommitRules.checkUnits(new CommitRules.Units(0, 0, 0, 0)), "至少 1 套");
        assertNotNull(CommitRules.checkUnits(new CommitRules.Units(5, 6, -1, 0)), "负数");
        assertNotNull(CommitRules.checkUnits(new CommitRules.Units(5, null, 1, 0)), "没填");
        assertNotNull(CommitRules.checkUnits(null));
    }

    @Test
    void missingAndText() {
        assertEquals(List.of("EQUIPMENT", "PROCESS"), CommitRules.missingAspects(Set.of("PRODUCTION", "SAFETY")));
        assertTrue(CommitRules.missingAspects(CommitRules.ASPECTS.keySet()).isEmpty());
        assertTrue(CommitRules.commitmentText(0).contains("各项安全风险防控措施已落实到位"));
        assertTrue(CommitRules.commitmentText(2).contains("发现 2 项异常"));
    }

    @Test
    void deadlinesAndRate() {
        LocalDate d = LocalDate.of(2026, 11, 7);
        assertFalse(CommitRules.judgeLate(d, d.atTime(9, 0), false));
        assertTrue(CommitRules.judgeLate(d, d.atTime(9, 0, 1), false));
        assertFalse(CommitRules.judgeLate(d, d.atTime(11, 0), true));
        assertFalse(CommitRules.commitLate(d, d.atTime(10, 0), false));
        assertTrue(CommitRules.commitLate(d, d.atTime(10, 1), false));
        assertFalse(CommitRules.commitLate(d, d.atTime(23, 0), true));
        assertTrue(CommitRules.commitLate(d, LocalDateTime.of(2026, 11, 8, 0, 30), false), "过了当天");
        assertTrue(CommitRules.onTime(d, d.atTime(10, 0)));
        assertFalse(CommitRules.onTime(d, d.atTime(10, 0, 1)));
        assertFalse(CommitRules.onTime(d, null));
        assertEquals(new BigDecimal("93.3"), CommitRules.rate(28, 30));
        assertEquals(new BigDecimal("100.0"), CommitRules.rate(7, 7));
        assertNull(CommitRules.rate(0, 0));
    }
}
