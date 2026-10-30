package com.safework.safety.dto;

import com.safework.safety.enums.RiskLevel;

import java.util.List;
import java.util.Map;

/**
 * 风险总览（四色分布）
 *
 * @param levels  各等级风险点数量
 * @param groups  按位置分组的风险点（画四色分布图）
 */
public record RiskOverviewVO(long total, Map<RiskLevel, Long> levels, long overdue, long dueToday,
                             long checks30d, long abnormal30d, List<Group> groups) {

    public record Group(String location, List<Point> points) {
    }

    public record Point(Long id, String code, String name, RiskLevel level, String state) {
    }
}
