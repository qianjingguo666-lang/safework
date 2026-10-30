package com.safework.ai.dto;

import com.safework.training.dto.ImportResult;

/**
 * AI 出题结果
 *
 * @param generated 模型生成了几道
 * @param saved     校验通过、以“待审核”状态入库的数量
 * @param skipped   没入库的题和原因
 */
public record AiQuestionResultVO(String documentTitle, int generated, int saved, java.util.List<ImportResult.Skipped> skipped,
                                 long costMs) {
}
