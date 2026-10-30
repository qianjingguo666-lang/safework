package com.safework.training.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.safework.training.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩和答卷回顾。
 *
 * @param showAnswer 为 false 时不返回正确答案和解析（items 里的 answer、analysis 为 null）
 */
public record ResultVO(Long recordId, String examTitle, String userName, Integer attemptNo,
                       Integer score, Integer totalScore, Integer passScore, boolean passed, boolean autoSubmit,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime submitTime,
                       long costSeconds, boolean showAnswer, int correctCount, List<Item> items) {

    public record Item(int index, QuestionType type, String content, List<String> options, int score,
                       String userAnswer, boolean correct, String answer, String analysis) {
    }
}
