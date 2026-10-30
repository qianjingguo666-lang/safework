package com.safework.ai.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 大模型的一次回复。
 *
 * @param message     choices[0].message：有 content 表示回答，有 tool_calls 表示要调用工具
 * @param totalTokens 这次调用消耗的 token 数（usage.total_tokens），用于统计成本
 */
public record ModelReply(ObjectNode message, int totalTokens) {
}
