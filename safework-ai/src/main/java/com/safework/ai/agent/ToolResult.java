package com.safework.ai.agent;

/**
 * 工具执行结果。
 *
 * @param data    返回给大模型的数据（Map / List，只放字符串、数字、布尔值）
 * @param summary 一句话摘要，显示在页面的“调用过程”里，例如“共 3 条”
 */
public record ToolResult(Object data, String summary) {
}
