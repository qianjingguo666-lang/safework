package com.safework.training.enums;

/** 题型。答案统一用选项字母表示：单选 "B"，多选 "ACD"，判断题的选项固定为【正确、错误】，答案 "A" 或 "B" */
public enum QuestionType {
    SINGLE("单选题"),
    MULTI("多选题"),
    JUDGE("判断题");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
