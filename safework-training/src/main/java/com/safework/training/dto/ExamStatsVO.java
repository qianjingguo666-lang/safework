package com.safework.training.dto;

import com.safework.training.entity.TrainExam;
import com.safework.training.enums.QuestionType;

import java.util.List;

/**
 * 考试统计（每人取最高分）
 *
 * @param targetUsers  应参加人数
 * @param buckets      分数段分布（按得分率：60% 以下、60~69% …）
 * @param hardest      正确率最低的题
 * @param notTaken     还没有交卷的人（最多 100 个）
 */
public record ExamStatsVO(TrainExam exam, int targetUsers, int submittedUsers, int passedUsers, Double passRate,
                          Double avgScore, Integer maxScore, Integer minScore, List<Bucket> buckets,
                          List<QuestionStat> hardest, List<String> notTaken) {

    public record Bucket(String name, int count) {
    }

    public record QuestionStat(Long questionId, QuestionType type, String content, int answered, int correct, double rate) {
    }
}
