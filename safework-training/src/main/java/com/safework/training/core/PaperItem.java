package com.safework.training.core;

import com.safework.training.enums.QuestionType;

import java.util.List;

/**
 * 试卷上的一道题（生成试卷时从题库复制一份快照）。
 * 快照的意义：考试过程中、考完以后有人修改或删除了题库里的题，已经生成的试卷和成绩不受影响。
 *
 * @param index      题号，从 1 开始
 * @param questionId 题库里的题目 ID（统计每道题的正确率用）
 * @param options    打乱顺序后的选项
 * @param answer     按打乱后的顺序换算过的答案字母
 */
public record PaperItem(int index, Long questionId, QuestionType type, String content, List<String> options,
                        String answer, String analysis, int score) {
}
