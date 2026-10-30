package com.safework.ai.dto;

/**
 * 当前用户可用的智能体工具（页面上展示“我能帮你做什么”）
 *
 * @param action 是否写操作（需要用户确认）
 */
public record AgentToolVO(String name, String label, String description, boolean action) {
}
