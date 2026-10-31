package com.safework.safety.emergency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应急管理的规则（纯计算，方便单元测试）：
 * <pre>
 * 1. 事故等级：按《生产安全事故报告和调查处理条例》，死亡、重伤人数决定最低等级，上报时不能报低
 * 2. 事件状态：已上报 → 响应中（启动预案）→ 已结束；误报可以从已上报直接结束
 * 3. 演练周期：综合预案、专项预案每年至少一次，现场处置方案每半年至少一次
 * </pre>
 */
public final class EmergencyRules {

    // ============================== 字典 ==============================

    public static final Map<String, String> ACCIDENT_TYPES = ordered(
            "FIRE_EXPLOSION", "火灾爆炸",
            "POISONING", "中毒窒息",
            "LEAK", "危化品泄漏",
            "FALL", "高处坠落",
            "ELECTRIC", "触电",
            "MECHANICAL", "机械伤害",
            "OTHER", "其他");

    /** 事故等级，从低到高 */
    public static final List<String> LEVELS = List.of("GENERAL", "LARGER", "MAJOR", "EXTRA");
    public static final Map<String, String> LEVEL_LABELS = ordered(
            "GENERAL", "一般事故", "LARGER", "较大事故", "MAJOR", "重大事故", "EXTRA", "特别重大事故");

    public static final String PLAN_COMPREHENSIVE = "COMPREHENSIVE";
    public static final String PLAN_SPECIAL = "SPECIAL";
    public static final String PLAN_ONSITE = "ONSITE";
    public static final Map<String, String> PLAN_TYPES = ordered(
            PLAN_COMPREHENSIVE, "综合应急预案", PLAN_SPECIAL, "专项应急预案", PLAN_ONSITE, "现场处置方案");

    public static final String REPORTED = "REPORTED";
    public static final String RESPONDING = "RESPONDING";
    public static final String CLOSED = "CLOSED";

    /** 演练状态 */
    public static final String DRILL_NEVER = "NEVER";
    public static final String DRILL_OVERDUE = "OVERDUE";
    public static final String DRILL_DUE_SOON = "DUE_SOON";
    public static final String DRILL_OK = "OK";

    private EmergencyRules() {
    }

    // ============================== 事故等级 ==============================

    /**
     * 按伤亡人数算出的最低等级（简化：只看死亡和重伤人数，不含直接经济损失）：
     * 死亡 30+ 或重伤 100+ 特别重大；死亡 10~29 或重伤 50~99 重大；死亡 3~9 或重伤 10~49 较大；其余一般
     */
    public static String minLevel(int dead, int seriousInjured) {
        if (dead >= 30 || seriousInjured >= 100) {
            return "EXTRA";
        }
        if (dead >= 10 || seriousInjured >= 50) {
            return "MAJOR";
        }
        if (dead >= 3 || seriousInjured >= 10) {
            return "LARGER";
        }
        return "GENERAL";
    }

    /** 上报的等级不能低于伤亡人数对应的等级，低了自动调高 */
    public static String effectiveLevel(String reported, int dead, int seriousInjured) {
        String min = minLevel(dead, seriousInjured);
        int r = LEVELS.indexOf(reported);
        return r < LEVELS.indexOf(min) ? min : reported;
    }

    // ============================== 事件状态 ==============================

    public enum Action {
        START(Set.of(REPORTED), RESPONDING, "启动应急响应"),
        UPDATE(Set.of(RESPONDING), RESPONDING, "处置记录"),
        CLOSE(Set.of(REPORTED, RESPONDING), CLOSED, "结束");

        public final Set<String> from;
        public final String to;
        public final String label;

        Action(Set<String> from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }

        public boolean canFire(String status) {
            return from.contains(status);
        }
    }

    // ============================== 演练周期 ==============================

    /** 演练周期（月）：现场处置方案半年，其他一年 */
    public static int drillCycleMonths(String planType) {
        return PLAN_ONSITE.equals(planType) ? 6 : 12;
    }

    /**
     * 演练状态
     *
     * @param nextDue 下次演练的最晚日期；从没演练过时为 null
     */
    public record DrillState(String state, LocalDate nextDue) {
    }

    public static DrillState drillState(String planType, LocalDateTime lastDrill, LocalDate today) {
        if (lastDrill == null) {
            return new DrillState(DRILL_NEVER, null);
        }
        LocalDate due = lastDrill.toLocalDate().plusMonths(drillCycleMonths(planType));
        if (today.isAfter(due)) {
            return new DrillState(DRILL_OVERDUE, due);
        }
        if (!today.plusDays(30).isBefore(due)) {
            return new DrillState(DRILL_DUE_SOON, due);
        }
        return new DrillState(DRILL_OK, due);
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
