package com.safework.training.core;

import com.safework.training.enums.QuestionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 组卷：从题库里按题型随机抽题，并打乱选择题的选项顺序（答案字母随之换算）。
 * 每个人、每次考试抽到的题和选项顺序都不一样，防止互相对答案。
 */
public final class PaperBuilder {

    private PaperBuilder() {
    }

    /** 题库里的一道题（组卷的输入） */
    public record Source(Long id, QuestionType type, String content, List<String> options, String answer, String analysis) {
    }

    /** 每种题型抽几道、每道几分 */
    public record Rule(QuestionType type, int count, int score) {
    }

    /**
     * @param pools 每种题型可用的题目
     * @throws IllegalStateException 某种题型的题目数量不够
     */
    public static List<PaperItem> build(Map<QuestionType, List<Source>> pools, List<Rule> rules, Random random) {
        List<PaperItem> paper = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule.count() <= 0) {
                continue;
            }
            List<Source> pool = new ArrayList<>(pools.getOrDefault(rule.type(), List.of()));
            if (pool.size() < rule.count()) {
                throw new IllegalStateException(rule.type().getLabel() + "题库里只有 " + pool.size() + " 道可用题目，需要 " + rule.count() + " 道");
            }
            Collections.shuffle(pool, random);
            for (Source q : pool.subList(0, rule.count())) {
                paper.add(item(paper.size() + 1, q, rule.score(), random));
            }
        }
        return paper;
    }

    static PaperItem item(int index, Source q, int score, Random random) {
        if (q.type() == QuestionType.JUDGE) {
            // 判断题“正确、错误”的顺序固定，不打乱
            return new PaperItem(index, q.id(), q.type(), q.content(), q.options(),
                    QuestionChecker.normalizeAnswer(q.answer()), q.analysis(), score);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        String correct = QuestionChecker.normalizeAnswer(q.answer());
        List<String> options = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        for (int newPos = 0; newPos < order.size(); newPos++) {
            int oldPos = order.get(newPos);
            options.add(q.options().get(oldPos));
            if (correct.indexOf(QuestionChecker.letter(oldPos)) >= 0) {
                answer.append(QuestionChecker.letter(newPos));
            }
        }
        return new PaperItem(index, q.id(), q.type(), q.content(), options, answer.toString(), q.analysis(), score);
    }
}
