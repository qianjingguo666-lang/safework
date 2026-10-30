package com.safework.ai.dto;

import java.util.List;

/**
 * 一句话提取出的隐患草稿：前端填进上报表单，上报人核对、修改后再提交（不会自动提交）
 *
 * @param spaceId   匹配到的受限空间，没有为 null
 * @param aiUsed    true 用了大模型；false 没配置大模型或者调用失败，按关键词提取
 * @param notes     需要上报人注意的地方（没听出位置、判定为重大隐患等）
 */
public record HazardDraftVO(String title, String category, String level, String location, Long spaceId, String spaceName,
                            String description, String levelReason, String text, boolean aiUsed, List<String> notes, long costMs) {
}
