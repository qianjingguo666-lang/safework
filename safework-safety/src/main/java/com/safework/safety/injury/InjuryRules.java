package com.safework.safety.injury;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工伤管理的规则（纯计算，方便单元测试），依据《工伤保险条例》：
 * <pre>
 * 第十七条：事故伤害发生之日起 30 日内，用人单位向社会保险行政部门提出工伤认定申请（特殊情况经批准可以延长）；
 *          单位没有按期申请的，这期间发生的工伤待遇等费用由单位负担 → 系统倒计时，到期前 7 / 3 / 1 / 0 天和超期后提醒
 * 第三十三条：停工留薪期一般不超过 12 个月，伤情严重或情况特殊的，经确认可以适当延长，但延长不得超过 12 个月
 * 第三十五 ~ 三十七条：一次性伤残补助金按伤残等级，一级 27 个月 ~ 十级 7 个月的本人工资
 * 流程：已登记 → 已申请认定 → 认定为工伤 / 不予认定 → 劳动能力鉴定（伤残等级，可以无等级）→ 结案
 * </pre>
 */
public final class InjuryRules {

    public static final int APPLY_DAYS = 30;
    public static final int LEAVE_MONTHS = 12;
    public static final int LEAVE_MONTHS_EXTENDED = 24;
    /** 剩余天数到这些天时提醒（含当天 0） */
    public static final List<Integer> REMIND_DAYS = List.of(7, 3, 1, 0);

    public static final String REPORTED = "REPORTED";
    public static final String APPLIED = "APPLIED";
    public static final String RECOGNIZED = "RECOGNIZED";
    public static final String NOT_RECOGNIZED = "NOT_RECOGNIZED";
    public static final String APPRAISED = "APPRAISED";
    public static final String CLOSED = "CLOSED";
    public static final Map<String, String> STATUS = ordered(REPORTED, "已登记，待申请认定", APPLIED, "已申请认定", RECOGNIZED, "认定为工伤",
            NOT_RECOGNIZED, "不予认定", APPRAISED, "已劳动能力鉴定", CLOSED, "已结案");

    public static final String OWN = "OWN";
    public static final String CONTRACTOR = "CONTRACTOR";

    public static final Map<String, String> COST_CATEGORIES = ordered("MEDICAL", "医疗费", "FOOD", "住院伙食补助费", "TRAVEL", "交通食宿费",
            "REHAB", "康复费", "NURSING", "护理费", "LEAVE_WAGE", "停工留薪期工资", "LUMP_SUM", "一次性伤残补助金", "OTHER", "其他");
    public static final Map<String, String> PAYERS = ordered("FUND", "工伤保险基金", "COMPANY", "单位");

    /** 一次性伤残补助金：伤残等级 → 本人工资的月数 */
    private static final int[] LUMP_SUM_MONTHS = {0, 27, 25, 23, 21, 18, 16, 13, 11, 9, 7};

    public static final String OK = "OK";
    public static final String SOON = "SOON";
    public static final String OVERDUE = "OVERDUE";
    public static final String DONE = "DONE";

    /** 申请期限：dueDate 最后一天，daysLeft 剩几天（负数为超期），state OK / SOON（7 天内）/ OVERDUE / DONE（已申请） */
    public record Deadline(LocalDate dueDate, long daysLeft, String state) {
    }

    /** 状态动作 */
    public enum Action {
        APPLY("申请工伤认定", List.of(REPORTED), APPLIED),
        RECOGNIZE("认定为工伤", List.of(APPLIED), RECOGNIZED),
        NOT_RECOGNIZE("不予认定", List.of(APPLIED), NOT_RECOGNIZED),
        APPRAISE("劳动能力鉴定", List.of(RECOGNIZED), APPRAISED),
        CLOSE("结案", List.of(RECOGNIZED, APPRAISED, NOT_RECOGNIZED), CLOSED);

        private final String label;
        private final List<String> from;
        private final String to;

        Action(String label, List<String> from, String to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String label() {
            return label;
        }

        public String to() {
            return to;
        }

        public boolean canFire(String status) {
            return from.contains(status);
        }
    }

    private InjuryRules() {
    }

    /** 申请工伤认定的期限：受伤之日起 30 日内 */
    public static Deadline applyDeadline(LocalDate injuryDate, String status, LocalDate today) {
        LocalDate due = injuryDate.plusDays(APPLY_DAYS);
        long left = ChronoUnit.DAYS.between(today, due);
        if (!REPORTED.equals(status)) {
            return new Deadline(due, left, DONE);
        }
        return new Deadline(due, left, left < 0 ? OVERDUE : left <= 7 ? SOON : OK);
    }

    /** 今天要不要提醒：剩 7 / 3 / 1 / 0 天，或者已经超期 */
    public static boolean shouldRemind(Deadline d) {
        return OVERDUE.equals(d.state()) || (SOON.equals(d.state()) && REMIND_DAYS.contains((int) d.daysLeft()));
    }

    /** 停工留薪期校验 */
    public static String checkLeave(LocalDate start, LocalDate end, boolean extended, LocalDate injuryDate) {
        if (start == null || end == null) {
            return "请填写停工留薪期的开始和结束日期";
        }
        if (start.isBefore(injuryDate)) {
            return "停工留薪期不能早于受伤日期";
        }
        if (end.isBefore(start)) {
            return "结束日期不能早于开始日期";
        }
        int max = extended ? LEAVE_MONTHS_EXTENDED : LEAVE_MONTHS;
        if (end.isAfter(start.plusMonths(max))) {
            return extended ? "停工留薪期延长后最长 24 个月" : "停工留薪期一般不超过 12 个月，伤情严重的勾选“经确认延长”（最长 24 个月）";
        }
        return null;
    }

    /** 一次性伤残补助金的月数；0 表示没有达到伤残等级 */
    public static int lumpSumMonths(int grade) {
        if (grade < 0 || grade > 10) {
            throw new IllegalArgumentException("伤残等级只能是 1 ~ 10 级（0 表示未达到等级）");
        }
        return LUMP_SUM_MONTHS[grade];
    }

    /** 一次性伤残补助金 = 月数 × 本人工资 */
    public static BigDecimal lumpSum(int grade, BigDecimal monthlyWage) {
        if (monthlyWage == null) {
            return null;
        }
        return monthlyWage.multiply(BigDecimal.valueOf(lumpSumMonths(grade))).setScale(2, RoundingMode.HALF_UP);
    }

    public static String gradeLabel(Integer grade) {
        if (grade == null) {
            return null;
        }
        return grade == 0 ? "未达到伤残等级" : new String[]{"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"}[grade] + "级伤残";
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
