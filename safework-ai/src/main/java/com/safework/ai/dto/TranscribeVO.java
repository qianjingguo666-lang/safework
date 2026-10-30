package com.safework.ai.dto;

/** 语音转文字结果 */
public record TranscribeVO(String text, double seconds, long costMs) {
}
