package com.safework.ai.dto;

/** 当前用户可用的智能体工具（页面上展示“我能帮你查什么”） */
public record AgentToolVO(String name, String label, String description) {
}
