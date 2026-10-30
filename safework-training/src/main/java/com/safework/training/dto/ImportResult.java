package com.safework.training.dto;

import java.util.List;

/**
 * 批量入库结果
 *
 * @param saved   入库数量
 * @param skipped 没有入库的题和原因（格式错误、和已有题目重复）
 */
public record ImportResult(int saved, List<Skipped> skipped) {

    public record Skipped(String content, String reason) {
    }
}
