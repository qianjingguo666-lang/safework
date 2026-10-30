package com.safework.safety.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/** 巡检提交结果 */
public record RiskCheckResultVO(Long checkId, String result, Long hazardId, String hazardNo,
                                @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime nextCheckTime) {
}
