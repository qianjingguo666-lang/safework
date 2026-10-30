package com.safework.training.core;

import com.safework.training.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("随机组卷")
class PaperBuilderTest {

    private static List<PaperBuilder.Source> singles(int n) {
        List<PaperBuilder.Source> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            // 正确答案永远是“对-i”这个选项，原始位置在 B
            list.add(new PaperBuilder.Source((long) i, QuestionType.SINGLE, "单选" + i,
                    List.of("错甲-" + i, "对-" + i, "错乙-" + i, "错丙-" + i), "B", "解析" + i));
        }
        return list;
    }

    private static final PaperBuilder.Source MULTI = new PaperBuilder.Source(100L, QuestionType.MULTI, "多选",
            List.of("对1", "错1", "对2", "错2"), "AC", null);
    private static final PaperBuilder.Source JUDGE = new PaperBuilder.Source(200L, QuestionType.JUDGE, "判断",
            QuestionChecker.JUDGE_OPTIONS, "B", null);

    @Test
    @DisplayName("按规则抽题：题号连续，分值正确，不重复")
    void rules() {
        List<PaperItem> paper = PaperBuilder.build(
                Map.of(QuestionType.SINGLE, singles(10), QuestionType.MULTI, List.of(MULTI), QuestionType.JUDGE, List.of(JUDGE)),
                List.of(new PaperBuilder.Rule(QuestionType.SINGLE, 3, 5), new PaperBuilder.Rule(QuestionType.MULTI, 1, 10),
                        new PaperBuilder.Rule(QuestionType.JUDGE, 1, 4)),
                new Random(1));
        assertEquals(5, paper.size());
        for (int i = 0; i < paper.size(); i++) {
            assertEquals(i + 1, paper.get(i).index());
        }
        assertEquals(29, Grader.totalScore(paper));
        Set<Long> ids = new HashSet<>();
        paper.forEach(p -> ids.add(p.questionId()));
        assertEquals(5, ids.size());
    }

    @Test
    @DisplayName("打乱选项后答案字母跟着换算，始终指向原来的正确选项")
    void shuffleKeepsAnswer() {
        Random random = new Random(42);
        boolean moved = false;
        for (int round = 0; round < 50; round++) {
            for (PaperItem item : PaperBuilder.build(Map.of(QuestionType.SINGLE, singles(5), QuestionType.MULTI, List.of(MULTI)),
                    List.of(new PaperBuilder.Rule(QuestionType.SINGLE, 5, 2), new PaperBuilder.Rule(QuestionType.MULTI, 1, 4)), random)) {
                for (char c : item.answer().toCharArray()) {
                    assertTrue(item.options().get(c - 'A').startsWith("对"), "答案 " + c + " 应该指向正确选项：" + item.options());
                }
                long correct = item.options().stream().filter(o -> o.startsWith("对")).count();
                assertEquals(correct, item.answer().length());
                if (item.type() == QuestionType.SINGLE && !"B".equals(item.answer())) {
                    moved = true;
                }
            }
        }
        assertTrue(moved, "选项应该被打乱过");
    }

    @Test
    @DisplayName("判断题不打乱“正确、错误”的顺序")
    void judgeNotShuffled() {
        for (int i = 0; i < 20; i++) {
            PaperItem item = PaperBuilder.build(Map.of(QuestionType.JUDGE, List.of(JUDGE)),
                    List.of(new PaperBuilder.Rule(QuestionType.JUDGE, 1, 2)), new Random(i)).get(0);
            assertEquals(QuestionChecker.JUDGE_OPTIONS, item.options());
            assertEquals("B", item.answer());
        }
    }

    @Test
    @DisplayName("题目不够时报错，提示缺多少")
    void notEnough() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PaperBuilder.build(
                Map.of(QuestionType.SINGLE, singles(2)), List.of(new PaperBuilder.Rule(QuestionType.SINGLE, 3, 2)), new Random()));
        assertTrue(e.getMessage().contains("只有 2 道") && e.getMessage().contains("需要 3 道"));
    }

    @Test
    @DisplayName("数量为 0 的题型跳过")
    void zeroCount() {
        assertEquals(1, PaperBuilder.build(Map.of(QuestionType.JUDGE, List.of(JUDGE)),
                List.of(new PaperBuilder.Rule(QuestionType.SINGLE, 0, 2), new PaperBuilder.Rule(QuestionType.JUDGE, 1, 2)),
                new Random()).size());
    }
}
