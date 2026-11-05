package com.safework.ticket.kpi;

import java.util.List;

/** 安全目标考核的返回对象 */
public final class KpiVOs {

    private KpiVOs() {
    }

    /**
     * 一个指标的完成情况
     *
     * @param actual    实际值（比例 %，或者次数）；没有数据为空
     * @param numerator 比例类的分子 / 分母（页面上显示“按期 38 / 40”）
     * @param months    1 ~ 12 月每月的实际值（未来的月份、没数据为空；只有当前值的指标为空列表）
     */
    public record Item(String code, String label, String unit, String direction, String source, String formula, boolean snapshot,
                       double target, int weight, Double actual, Long numerator, Long denominator, String light, Double score,
                       List<Double> months) {
    }

    /**
     * 年度报告
     *
     * @param defaultTargets 这一年还没设置目标，用的是系统默认目标
     * @param asOf           统计到哪一天
     */
    public record Report(int year, String asOf, boolean defaultTargets, Double totalScore, int green, int yellow, int red,
                         List<Item> items) {
    }

    public record TargetRow(String code, String label, String unit, String direction, double target, int weight) {
    }
}
