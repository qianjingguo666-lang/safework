package com.safework.ai.dto;

import com.safework.ai.core.TicketRuleChecker;
import lombok.Data;

import java.util.List;

/** 作业票预审结果：规则检查（一定有）+ 大模型分析（配置了才有） */
@Data
public class ReviewResultVO {
    /** 综合风险：HIGH / MEDIUM / LOW */
    private String riskLevel;
    private List<TicketRuleChecker.Finding> ruleFindings;
    private boolean aiAvailable;
    /** 大模型调用失败的原因（失败不影响规则检查结果） */
    private String aiError;
    private AiReview ai;
    private List<SourceVO> sources;
    private long costMs;

    @Data
    public static class AiReview {
        private String riskLevel;
        /** 建议通过 / 建议补充后再审批 / 建议驳回 */
        private String conclusion;
        private List<TicketRuleChecker.Finding> issues;
        private String summary;
    }
}
