package com.safework.safety.contractor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 承包商规则（纯函数，方便单元测试）：
 * <pre>
 * 待审核 ──通过──▶ 合格 ──拉黑──▶ 黑名单 ──解除──▶ 待审核（重新审核）
 *    └──驳回──▶ 已驳回 ──修改后重新提交──▶ 待审核
 * </pre>
 * 安全评分：100 分起，近 12 个月的违章扣分累计；低于 60 分自动列入黑名单。
 */
public final class ContractorRules {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String BLACKLISTED = "BLACKLISTED";
    public static final Map<String, String> STATUS_LABELS = Map.of(
            PENDING, "待审核", APPROVED, "合格", REJECTED, "已驳回", BLACKLISTED, "黑名单");

    public static final int FULL_SCORE = 100;
    /** 低于这个分数自动列入黑名单 */
    public static final int BLACKLIST_LINE = 60;
    /** 单次违章最多扣多少分 */
    public static final int MAX_POINTS = 20;
    /** 违章扣分统计近多少个月 */
    public static final int SCORE_MONTHS = 12;
    /** 入厂安全培训有效期（月） */
    public static final int TRAINING_MONTHS = 12;
    /** 提前多少天提示“即将到期” */
    public static final int WARN_DAYS = 30;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum Action {
        APPROVE("审核通过", PENDING, APPROVED),
        REJECT("驳回", PENDING, REJECTED),
        RESUBMIT("重新提交审核", REJECTED, PENDING),
        BLACKLIST("列入黑名单", APPROVED, BLACKLISTED),
        LIFT("解除黑名单", BLACKLISTED, PENDING);

        public final String label;
        public final String from;
        public final String to;

        Action(String label, String from, String to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public boolean allowedFrom(String status) {
            return from.equals(status);
        }
    }

    private ContractorRules() {
    }

    public static int score(int deductedPoints) {
        return Math.max(0, FULL_SCORE - deductedPoints);
    }

    public static boolean shouldBlacklist(int score) {
        return score < BLACKLIST_LINE;
    }

    /** 培训日期 → 培训有效期 */
    public static LocalDate trainingExpire(LocalDate trainingDate) {
        return trainingDate == null ? null : trainingDate.plusMonths(TRAINING_MONTHS);
    }

    /**
     * 承包商能不能进场作业
     *
     * @param workEnd 作业结束日期：资质、保险要一直有效到这一天
     */
    public static List<String> contractorProblems(String status, int score, LocalDate licenseExpire, LocalDate insuranceExpire,
                                                  LocalDate workEnd, LocalDate today) {
        List<String> result = new ArrayList<>();
        if (!APPROVED.equals(status)) {
            result.add("单位状态是【" + STATUS_LABELS.getOrDefault(status, status) + "】，只有审核合格的承包商可以作业");
            return result;
        }
        if (shouldBlacklist(score)) {
            result.add("安全评分 " + score + " 分，低于 " + BLACKLIST_LINE + " 分");
        }
        dateProblem(result, "资质证书", licenseExpire, workEnd, today);
        dateProblem(result, "安全生产责任险", insuranceExpire, workEnd, today);
        return result;
    }

    /** 承包商作业人员能不能进场：账号在用、入厂安全培训在有效期内 */
    public static List<String> workerProblems(boolean active, LocalDate trainingDate, LocalDate workEnd, LocalDate today) {
        List<String> result = new ArrayList<>();
        if (!active) {
            result.add("已停用（离场）");
            return result;
        }
        if (trainingDate == null) {
            result.add("没有参加入厂安全培训");
        } else {
            dateProblem(result, "入厂安全培训", trainingExpire(trainingDate), workEnd, today);
        }
        return result;
    }

    /** 日期没填当作缺失；过期；作业期间到期 */
    static void dateProblem(List<String> out, String what, LocalDate expire, LocalDate workEnd, LocalDate today) {
        if (expire == null) {
            out.add(what + "没有登记有效期");
        } else if (expire.isBefore(today)) {
            out.add(what + "已过期（" + expire.format(DATE) + "）");
        } else if (workEnd != null && expire.isBefore(workEnd)) {
            out.add(what + "到 " + expire.format(DATE) + "，早于作业结束时间");
        }
    }

    /** 页面提示：30 天内有东西到期 */
    public static boolean expiringSoon(LocalDate expire, LocalDate today) {
        return expire != null && !expire.isBefore(today) && !expire.isAfter(today.plusDays(WARN_DAYS));
    }
}
