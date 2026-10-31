package com.safework.ai.dto;

import java.util.List;

/**
 * 应急处置建议（只是建议，启动哪个预案由安全员决定）
 *
 * @param planId     建议启动的预案，没有匹配到为 null
 * @param candidates 规则粗筛出的候选预案（前端可以改选）
 * @param aiUsed     true 大模型给出的建议；false 按规则从预案原文里摘出来的
 */
public record EmergencyAdviceVO(Long planId, String planCode, String planTitle, String reason, List<String> steps,
                                List<String> cautions, List<Candidate> candidates, boolean aiUsed, String note, long costMs) {

    public record Candidate(Long id, String code, String title) {
    }
}
