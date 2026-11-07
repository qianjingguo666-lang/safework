package com.safework.safety.service;

import com.safework.safety.enums.HazardAction;

import java.time.LocalDateTime;

/**
 * 隐患流转了一步（上报、指派、整改、验收……）。在隐患事务提交后由其他模块监听处理（例如隐患随手拍积分），
 * 隐患模块不用知道有谁在监听。
 */
public record HazardFiredEvent(Long hazardId, String hazardNo, String title, String level, HazardAction action,
                               Long reporterId, String reporterName, Long rectifierId, String rectifierName,
                               LocalDateTime deadline, LocalDateTime rectifyTime, String operatorName) {
}
