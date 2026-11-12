package com.safework.safety.observation;

import java.time.LocalDateTime;
import java.util.Map;

public final class SafetyObservationRules {
    public static final String REPORTED = "REPORTED";
    public static final String ANALYZING = "ANALYZING";
    public static final String CLOSED = "CLOSED";

    public static final Map<String, String> TYPES = Map.of(
            "NEAR_MISS", "未遂事件",
            "UNSAFE_BEHAVIOR", "不安全行为",
            "UNSAFE_CONDITION", "不安全状态",
            "GOOD_PRACTICE", "安全观察");
    public static final Map<String, String> LEVELS = Map.of(
            "LOW", "低", "MEDIUM", "中", "HIGH", "高");

    private SafetyObservationRules() {
    }

    public static String reportError(String type, String level, LocalDateTime occurTime, LocalDateTime now) {
        if (!TYPES.containsKey(type)) return "观察类型不正确";
        if (!LEVELS.containsKey(level)) return "潜在风险等级不正确";
        if (occurTime == null) return "请选择发生时间";
        if (occurTime.isAfter(now)) return "发生时间不能晚于现在";
        return null;
    }

    public static String analysisError(String type, String cause, String action, boolean toHazard) {
        if (!TYPES.containsKey(type)) return "观察类型不正确";
        if (cause == null || cause.isBlank()) return "请填写原因分析";
        if (action == null || action.isBlank()) return "请填写纠正和预防措施";
        if (toHazard && "GOOD_PRACTICE".equals(type)) return "安全观察不能转为隐患";
        return null;
    }

    public static String closeError(String cause, String action) {
        if (cause == null || cause.isBlank()) return "请先完成原因分析";
        if (action == null || action.isBlank()) return "请先制定纠正和预防措施";
        return null;
    }

    public static String hazardCategory(String type) {
        return switch (type) {
            case "UNSAFE_BEHAVIOR" -> "人员行为";
            case "UNSAFE_CONDITION" -> "作业环境";
            case "NEAR_MISS" -> "安全管理";
            default -> throw new IllegalArgumentException("该观察类型不能转为隐患");
        };
    }
}
