package com.safework.ai.dto;

/** 发给大模型的一条消息：role 为 system / user / assistant */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
