package com.safework.ai.core.emergency;

/** 参与匹配的预案（精简信息） */
public record PlanCandidate(Long id, String code, String title, String planType, String accidentType, String scope, String content) {
}
