package com.safework.training.core;

import com.safework.training.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("题目校验")
class QuestionCheckerTest {

    private static final List<String> FOUR = List.of("甲", "乙", "丙", "丁");

    @Test
    @DisplayName("合格的单选、多选、判断题")
    void valid() {
        assertNull(QuestionChecker.check(QuestionType.SINGLE, "题干", FOUR, "b"));
        assertNull(QuestionChecker.check(QuestionType.MULTI, "题干", FOUR, "D,A"));
        assertNull(QuestionChecker.check(QuestionType.JUDGE, "题干", QuestionChecker.JUDGE_OPTIONS, "A"));
    }

    @Test
    @DisplayName("答案数量不对：单选两个答案、多选只有一个、多选全选")
    void answerCount() {
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "题干", FOUR, "AB").contains("只能有 1 个"));
        assertTrue(QuestionChecker.check(QuestionType.MULTI, "题干", FOUR, "A").contains("至少要有 2 个"));
        assertTrue(QuestionChecker.check(QuestionType.MULTI, "题干", FOUR, "ABCD").contains("不能所有选项"));
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "题干", FOUR, "").contains("正确答案"));
    }

    @Test
    @DisplayName("答案超出选项范围、选项重复、选项为空")
    void options() {
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "题干", List.of("甲", "乙"), "C").contains("超出"));
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "题干", List.of("甲", "甲 "), "A").contains("重复"));
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "题干", Arrays.asList("甲", null), "A").contains("不能为空"));
        assertTrue(QuestionChecker.check(QuestionType.MULTI, "题干", List.of("甲", "乙"), "AB").contains("3~6"));
        assertTrue(QuestionChecker.check(QuestionType.JUDGE, "题干", List.of("对", "错"), "A").contains("正确、错误"));
    }

    @Test
    @DisplayName("题干为空、过长")
    void content() {
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, " ", FOUR, "A").contains("题干"));
        assertTrue(QuestionChecker.check(QuestionType.SINGLE, "长".repeat(501), FOUR, "A").contains("500"));
    }

    @Test
    @DisplayName("答案标准化：去掉标点、转大写、去重、排序")
    void normalize() {
        assertEquals("AC", QuestionChecker.normalizeAnswer("c, a, C"));
        assertEquals("", QuestionChecker.normalizeAnswer(null));
        assertEquals("B", QuestionChecker.normalizeAnswer(" b "));
    }
}
