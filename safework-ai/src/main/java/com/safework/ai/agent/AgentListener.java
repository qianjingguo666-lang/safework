package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

/** 智能体执行过程中的事件，用于实时推送给页面 */
public interface AgentListener {

    /** 第 round 次请求大模型 */
    default void thinking(int round) {
    }

    default void toolStart(String callId, String name, String label, JsonNode args) {
    }

    default void toolEnd(String callId, String name, boolean ok, String summary, long costMs) {
    }

    default void answer(String text) {
    }
}
