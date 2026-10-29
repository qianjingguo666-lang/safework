package com.safework.ai.dto;

public record AiStatusVO(boolean chatEnabled, String chatModel, boolean embeddingEnabled, String embeddingModel,
                         long documentCount, long chunkCount) {
}
