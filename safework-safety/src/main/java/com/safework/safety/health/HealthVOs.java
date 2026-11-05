package com.safework.safety.health;

import com.safework.safety.entity.OhExam;

import java.time.LocalDate;
import java.util.List;

/** 职业健康的返回对象 */
public final class HealthVOs {

    private HealthVOs() {
    }

    /** 岗位 + 接触的危害因素 */
    public record PostFactors(Long postId, String postName, Integer status, int memberCount, List<Long> factorIds, List<String> factorNames) {
    }

    /**
     * 监护台账一行（一个接害人员）
     *
     * @param cycleMonths      体检周期（接触的因素里最短的）
     * @param contraindication 最近一次体检是职业禁忌证（不应该继续在接害岗位）
     * @param openAbnormal     未处理的异常体检数
     */
    public record LedgerRow(Long userId, String userName, String posts, String factors, int cycleMonths,
                            LocalDate lastExamDate, String lastConclusion, LocalDate nextExamDate, Long daysLeft, String state,
                            boolean contraindication, int openAbnormal) {
    }

    public record LedgerPage(List<LedgerRow> records, long total) {
    }

    /**
     * 统计
     *
     * @param exposed          接害人数
     * @param never            从没做过上岗前 / 在岗体检
     * @param overdue          体检逾期
     * @param dueSoon          30 天内到期
     * @param abnormalOpen     异常未处理
     * @param contraindication 禁忌证人员仍在接害岗位
     */
    public record Overview(int exposed, int never, int overdue, int dueSoon, int abnormalOpen, int contraindication) {
    }

    /** 我的职业健康：我的台账（不接害为空）+ 我的体检记录 */
    public record Mine(LedgerRow ledger, List<OhExam> exams) {
    }
}
