package com.safework.ai.dto;

/** 回答引用的知识库片段：index 对应回答里的 [1] [2]。documentId 用字符串，避免前端 JS 精度丢失 */
public record SourceVO(int index, String documentId, String documentTitle, Integer pageNo, String content, double score) {
}
