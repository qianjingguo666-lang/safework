package com.safework.training.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.safework.training.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 答题时的试卷：不包含正确答案和解析。
 *
 * @param deadline  交卷截止时间（倒计时以服务器时间为准，前端用 serverNow 校正本机时钟误差）
 * @param answers   已保存的答案（刷新页面、换电脑后可以继续答）
 */
public record PaperVO(Long recordId, String examTitle, Integer attemptNo,
                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline,
                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime serverNow,
                      Integer durationMinutes, Integer totalScore, Integer passScore,
                      List<Question> items, Map<Integer, String> answers) {

    public record Question(int index, QuestionType type, String content, List<String> options, int score) {
    }
}
