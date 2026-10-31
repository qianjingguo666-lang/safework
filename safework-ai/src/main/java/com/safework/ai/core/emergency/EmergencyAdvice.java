package com.safework.ai.core.emergency;

import java.util.List;

/**
 * 应急处置建议
 *
 * @param plan     建议启动的预案，匹配不到为 null
 * @param steps    立即处置要点（来自预案原文）
 * @param cautions 特别注意（例如“严禁盲目施救”）
 */
public record EmergencyAdvice(PlanCandidate plan, String reason, List<String> steps, List<String> cautions) {
}
