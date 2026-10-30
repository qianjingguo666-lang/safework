package com.safework.training.core;

import com.safework.training.enums.QuestionType;

import java.util.List;
import java.util.TreeSet;

/**
 * 题目校验（人工录入和 AI 出题共用）。AI 生成的题目经常有格式问题：多选题只有一个答案、答案字母超出选项范围、
 * 选项重复……这些题如果直接进题库，考试时会判错分，所以必须在入库前拦下来。
 */
public final class QuestionChecker {

    public static final List<String> JUDGE_OPTIONS = List.of("正确", "错误");
    public static final int MAX_CONTENT = 500;
    public static final int MAX_OPTION = 200;

    private QuestionChecker() {
    }

    /** 校验通过返回 null，否则返回原因 */
    public static String check(QuestionType type, String content, List<String> options, String answer) {
        if (type == null) {
            return "题型不能为空";
        }
        if (content == null || content.isBlank()) {
            return "题干不能为空";
        }
        if (content.length() > MAX_CONTENT) {
            return "题干不能超过 " + MAX_CONTENT + " 字";
        }
        if (options == null) {
            return "选项不能为空";
        }
        if (type == QuestionType.JUDGE) {
            if (!JUDGE_OPTIONS.equals(options)) {
                return "判断题的选项必须是【正确、错误】";
            }
        } else {
            int min = type == QuestionType.SINGLE ? 2 : 3;
            if (options.size() < min || options.size() > 6) {
                return type.getLabel() + "需要 " + min + "~6 个选项";
            }
            for (String o : options) {
                if (o == null || o.isBlank()) {
                    return "选项不能为空";
                }
                if (o.length() > MAX_OPTION) {
                    return "选项不能超过 " + MAX_OPTION + " 字";
                }
            }
            if (options.stream().map(String::trim).distinct().count() != options.size()) {
                return "选项不能重复";
            }
        }
        String a = normalizeAnswer(answer);
        if (a.isEmpty()) {
            return "请设置正确答案";
        }
        for (char c : a.toCharArray()) {
            if (c - 'A' >= options.size()) {
                return "答案 " + c + " 超出了选项范围";
            }
        }
        if (type == QuestionType.MULTI) {
            if (a.length() < 2) {
                return "多选题至少要有 2 个正确答案";
            }
            if (a.length() == options.size()) {
                return "多选题不能所有选项都正确";
            }
        } else if (a.length() != 1) {
            return type.getLabel() + "只能有 1 个正确答案";
        }
        return null;
    }

    /**
     * 答案标准化：只保留 A~F，去重、排序、转大写。
     * "c, a" → "AC"；"B" → "B"；null → ""
     */
    public static String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        TreeSet<Character> set = new TreeSet<>();
        for (char c : answer.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'F') {
                set.add(c);
            }
        }
        StringBuilder sb = new StringBuilder();
        set.forEach(sb::append);
        return sb.toString();
    }

    /** 第 i 个选项的字母：0 → A */
    public static char letter(int i) {
        return (char) ('A' + i);
    }
}
