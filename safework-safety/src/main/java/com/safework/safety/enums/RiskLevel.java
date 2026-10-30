package com.safework.safety.enums;

/**
 * 风险等级（四色）。管控层级和默认巡检周期按“风险越高、管控层级越高、检查越频繁”的原则确定。
 */
public enum RiskLevel {
    MAJOR("重大风险", "红", "公司级", 1),
    HIGH("较大风险", "橙", "部门级", 7),
    GENERAL("一般风险", "黄", "班组级", 15),
    LOW("低风险", "蓝", "岗位级", 30);

    private final String label;
    private final String color;
    private final String controlLevel;
    private final int defaultCycleDays;

    RiskLevel(String label, String color, String controlLevel, int defaultCycleDays) {
        this.label = label;
        this.color = color;
        this.controlLevel = controlLevel;
        this.defaultCycleDays = defaultCycleDays;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public String getControlLevel() {
        return controlLevel;
    }

    public int getDefaultCycleDays() {
        return defaultCycleDays;
    }
}
