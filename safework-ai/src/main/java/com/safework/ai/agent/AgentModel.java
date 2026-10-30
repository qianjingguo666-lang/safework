package com.safework.ai.agent;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * 支持 Function Calling 的大模型。抽成接口是为了单元测试时可以用“剧本”代替真实模型。
 */
@FunctionalInterface
public interface AgentModel {

    /**
     * 调用一次大模型（非流式）。
     *
     * @param messages 完整对话：system / user / assistant（可能带 tool_calls）/ tool
     * @param tools    可用工具定义；传 null 表示这次不允许调用工具，必须直接回答
     */
    ModelReply call(ArrayNode messages, ArrayNode tools);
}
