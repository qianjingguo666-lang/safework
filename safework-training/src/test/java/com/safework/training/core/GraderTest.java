package com.safework.training.core;

import com.safework.training.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("阅卷")
class GraderTest {

    private static final List<PaperItem> PAPER = List.of(
            new PaperItem(1, 11L, QuestionType.SINGLE, "单选", List.of("a", "b", "c", "d"), "B", null, 5),
            new PaperItem(2, 12L, QuestionType.MULTI, "多选", List.of("a", "b", "c", "d"), "ACD", null, 10),
            new PaperItem(3, 13L, QuestionType.JUDGE, "判断", QuestionChecker.JUDGE_OPTIONS, "A", null, 5));

    @Test
    @DisplayName("多选题必须完全一致：少选、多选都不得分；顺序和大小写不影响")
    void multi() {
        assertTrue(Grader.isCorrect("ACD", "dca"));
        assertFalse(Grader.isCorrect("ACD", "AC"));
        assertFalse(Grader.isCorrect("ACD", "ABCD"));
        assertFalse(Grader.isCorrect("ACD", null));
    }

    @Test
    @DisplayName("算总分：全对满分、部分对、没作答 0 分")
    void score() {
        assertEquals(20, Grader.totalScore(PAPER));
        assertEquals(20, Grader.score(PAPER, Map.of(1, "B", 2, "CAD", 3, "A")));
        assertEquals(10, Grader.score(PAPER, Map.of(1, "B", 2, "AC", 3, "A")));
        assertEquals(0, Grader.score(PAPER, Map.of()));
        assertEquals(0, Grader.score(PAPER, null));
    }
}
