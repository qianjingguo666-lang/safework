package com.safework.ai.core.hazard;

/** 受限空间（给提取规则和大模型用的精简信息） */
public record SpaceRef(String code, String name, String location) {
}
