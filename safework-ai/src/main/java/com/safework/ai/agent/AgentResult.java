package com.safework.ai.agent;

import java.util.List;

/**
 * 一次智能体问答的结果。
 *
 * @param answer      最终回答
 * @param steps       调用过的工具
 * @param rounds      请求了几次大模型
 * @param totalTokens 总 token 消耗
 * @param limited     是否因为达到最大轮数被强制要求直接回答
 */
public record AgentResult(String answer, List<Step> steps, int rounds, int totalTokens, boolean limited) {

    /** 一次工具调用 */
    public record Step(String name, String label, String args, boolean ok, String summary, long costMs) {
    }
}
