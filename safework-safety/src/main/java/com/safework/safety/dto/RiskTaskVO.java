package com.safework.safety.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.safework.safety.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

/** 巡检任务：一个需要巡检的风险点 */
public record RiskTaskVO(Long pointId, String pointCode, String pointName, String pointType, String location,
                         RiskLevel riskLevel, String responsibleName, Integer checkCycleDays,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime lastCheckTime,
                         @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime nextCheckTime,
                         String state, long daysLeft, List<String> measures) {
}
