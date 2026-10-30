package com.safework.ai.dto;

/** @param asrEnabled 是否配置了讯飞语音听写（语音上报隐患用） */
public record AiStatusVO(boolean chatEnabled, String chatModel, boolean embeddingEnabled, String embeddingModel,
                         long documentCount, long chunkCount, boolean asrEnabled) {
}
