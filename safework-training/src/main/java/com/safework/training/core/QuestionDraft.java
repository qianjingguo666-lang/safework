package com.safework.training.core;

import com.safework.training.enums.QuestionType;

import java.util.List;

/** 待入库的题目（AI 生成后先转成这个，校验通过才保存） */
public record QuestionDraft(QuestionType type, String content, List<String> options, String answer, String analysis) {
}
