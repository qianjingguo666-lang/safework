package com.safework.training.core;

import java.util.List;
import java.util.Map;

/**
 * 阅卷。规则：答案完全一致才得分（多选题少选、多选、错选都不得分，和安全资格考试一致）。
 */
public final class Grader {

    private Grader() {
    }

    public static boolean isCorrect(String correctAnswer, String userAnswer) {
        String c = QuestionChecker.normalizeAnswer(correctAnswer);
        return !c.isEmpty() && c.equals(QuestionChecker.normalizeAnswer(userAnswer));
    }

    /**
     * 计算总分。
     *
     * @param answers 题号（从 1 开始）→ 考生答案
     */
    public static int score(List<PaperItem> paper, Map<Integer, String> answers) {
        int total = 0;
        for (PaperItem item : paper) {
            if (isCorrect(item.answer(), answers == null ? null : answers.get(item.index()))) {
                total += item.score();
            }
        }
        return total;
    }

    public static int totalScore(List<PaperItem> paper) {
        return paper.stream().mapToInt(PaperItem::score).sum();
    }
}
