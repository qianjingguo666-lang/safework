package com.safework.safety.contractor;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractorRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private static final LocalDate END = TODAY.plusDays(2);

    @Test
    void stateMachine() {
        assertTrue(ContractorRules.Action.APPROVE.allowedFrom(ContractorRules.PENDING));
        assertFalse(ContractorRules.Action.APPROVE.allowedFrom(ContractorRules.REJECTED));
        assertTrue(ContractorRules.Action.BLACKLIST.allowedFrom(ContractorRules.APPROVED));
        assertFalse(ContractorRules.Action.BLACKLIST.allowedFrom(ContractorRules.PENDING));
        // 解除黑名单要重新审核，不能直接恢复合格
        assertEquals(ContractorRules.PENDING, ContractorRules.Action.LIFT.to);
    }

    @Test
    void score() {
        assertEquals(100, ContractorRules.score(0));
        assertEquals(55, ContractorRules.score(45));
        assertEquals(0, ContractorRules.score(130));
        assertFalse(ContractorRules.shouldBlacklist(60));
        assertTrue(ContractorRules.shouldBlacklist(59));
    }

    @Test
    void contractorProblems() {
        LocalDate ok = TODAY.plusYears(1);
        assertTrue(ContractorRules.contractorProblems(ContractorRules.APPROVED, 95, ok, ok, END, TODAY).isEmpty());
        List<String> p = ContractorRules.contractorProblems(ContractorRules.PENDING, 100, ok, ok, END, TODAY);
        assertTrue(p.size() == 1 && p.get(0).contains("待审核"));
        p = ContractorRules.contractorProblems(ContractorRules.APPROVED, 100, TODAY.minusDays(1), TODAY.plusDays(1), END, TODAY);
        assertEquals(2, p.size());
        assertTrue(p.get(0).contains("资质证书已过期"));
        assertTrue(p.get(1).contains("早于作业结束时间"));
        p = ContractorRules.contractorProblems(ContractorRules.APPROVED, 100, null, ok, END, TODAY);
        assertTrue(p.get(0).contains("没有登记"));
    }

    @Test
    void workerProblems() {
        assertTrue(ContractorRules.workerProblems(true, TODAY.minusMonths(3), END, TODAY).isEmpty());
        assertTrue(ContractorRules.workerProblems(false, TODAY, END, TODAY).get(0).contains("停用"));
        assertTrue(ContractorRules.workerProblems(true, null, END, TODAY).get(0).contains("没有参加"));
        // 13 个月前培训的，已经过期
        assertTrue(ContractorRules.workerProblems(true, TODAY.minusMonths(13), END, TODAY).get(0).contains("已过期"));
        // 培训明天到期，作业后天结束
        assertTrue(ContractorRules.workerProblems(true, TODAY.plusDays(1).minusMonths(12), END, TODAY).get(0).contains("早于作业结束时间"));
        assertEquals(TODAY.plusMonths(12), ContractorRules.trainingExpire(TODAY));
    }

    @Test
    void expiringSoon() {
        assertTrue(ContractorRules.expiringSoon(TODAY, TODAY));
        assertTrue(ContractorRules.expiringSoon(TODAY.plusDays(30), TODAY));
        assertFalse(ContractorRules.expiringSoon(TODAY.plusDays(31), TODAY));
        assertFalse(ContractorRules.expiringSoon(TODAY.minusDays(1), TODAY));
    }
}
