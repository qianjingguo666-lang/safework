package com.safework.safety.health;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 职业健康规则（纯函数，方便单元测试），依据《职业健康监护技术规范》（GBZ 188）的思路做了简化：
 * <ul>
 *   <li>岗位接触哪些危害因素，在这个岗位上的人就要做职业健康检查；多种因素取最短的体检周期</li>
 *   <li>上岗前体检、在岗体检算作“最近一次体检”，加上周期就是下次体检日期；离岗体检、应急体检不算</li>
 *   <li>需要复查的必须约好复查日期（90 天内）；需要复查、疑似职业病、职业禁忌证都是异常，要登记处理措施才算处理完</li>
 * </ul>
 */
public final class HealthRules {

    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    public static final Map<String, String> EXAM_TYPES = new LinkedHashMap<>();
    public static final Map<String, String> CONCLUSIONS = new LinkedHashMap<>();

    static {
        CATEGORIES.put("CHEMICAL", "化学因素");
        CATEGORIES.put("DUST", "粉尘");
        CATEGORIES.put("PHYSICAL", "物理因素");
        CATEGORIES.put("BIOLOGICAL", "生物因素");
        CATEGORIES.put("RADIATION", "放射性因素");
        EXAM_TYPES.put("PRE", "上岗前");
        EXAM_TYPES.put("ON", "在岗期间");
        EXAM_TYPES.put("LEAVE", "离岗时");
        EXAM_TYPES.put("EMERGENCY", "应急");
        CONCLUSIONS.put("NORMAL", "目前未见异常");
        CONCLUSIONS.put("OTHER", "其他疾病或异常");
        CONCLUSIONS.put("RECHECK", "需要复查");
        CONCLUSIONS.put("SUSPECT", "疑似职业病");
        CONCLUSIONS.put("CONTRAINDICATION", "职业禁忌证");
    }

    /** 体检状态 */
    public static final String NEVER = "NEVER";
    public static final String OVERDUE = "OVERDUE";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String OK = "OK";

    /** 异常处理状态：NONE 不需要处理 / OPEN 待处理 / CLOSED 已处理 */
    public static final String NONE = "NONE";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    public static final int DEFAULT_CYCLE = 12;
    public static final int WARN_DAYS = 30;
    public static final int MAX_RECHECK_DAYS = 90;

    /** 需要处理的结论 */
    private static final Set<String> ABNORMAL = Set.of("RECHECK", "SUSPECT", "CONTRAINDICATION");
    /** 算作“最近一次体检”的类型 */
    private static final Set<String> CYCLE_TYPES = Set.of("PRE", "ON");

    private HealthRules() {
    }

    public static boolean isAbnormal(String conclusion) {
        return ABNORMAL.contains(conclusion);
    }

    public static boolean countsForCycle(String examType) {
        return CYCLE_TYPES.contains(examType);
    }

    /** 体检周期：接触的危害因素里最短的；没有设置时 12 个月 */
    public static int cycle(Collection<Integer> factorCycles) {
        return factorCycles.stream().filter(c -> c != null && c > 0).min(Integer::compare).orElse(DEFAULT_CYCLE);
    }

    public static LocalDate nextExam(LocalDate last, int cycleMonths) {
        return last == null ? null : last.plusMonths(cycleMonths);
    }

    /** 下次体检为空 = 从没做过上岗前 / 在岗体检 */
    public static String state(LocalDate next, LocalDate today) {
        if (next == null) {
            return NEVER;
        }
        if (next.isBefore(today)) {
            return OVERDUE;
        }
        return next.isAfter(today.plusDays(WARN_DAYS)) ? OK : DUE_SOON;
    }

    /** 提醒：到期前 30 天、7 天、当天；逾期后每 7 天 */
    public static boolean shouldRemind(LocalDate next, LocalDate today) {
        if (next == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(today, next);
        if (days == 30 || days == 7 || days == 0) {
            return true;
        }
        return days < 0 && (-days - 1) % 7 == 0;
    }

    /**
     * 体检记录校验
     *
     * @return 错误信息，没问题返回 null
     */
    public static String checkExam(String type, String conclusion, LocalDate examDate, LocalDate recheckDate, LocalDate today) {
        if (!EXAM_TYPES.containsKey(type)) {
            return "体检类型不正确";
        }
        if (!CONCLUSIONS.containsKey(conclusion)) {
            return "体检结论不正确";
        }
        if (examDate == null || examDate.isAfter(today)) {
            return "体检日期不能晚于今天";
        }
        if (examDate.isBefore(today.minusYears(3))) {
            return "只能补录 3 年以内的体检";
        }
        if ("RECHECK".equals(conclusion)) {
            if (recheckDate == null) {
                return "需要复查的，请填写复查日期";
            }
            if (!recheckDate.isAfter(examDate) || recheckDate.isAfter(examDate.plusDays(MAX_RECHECK_DAYS))) {
                return "复查日期要在体检之后 " + MAX_RECHECK_DAYS + " 天内";
            }
        } else if (recheckDate != null) {
            return "只有“需要复查”的结论才填写复查日期";
        }
        return null;
    }
}
