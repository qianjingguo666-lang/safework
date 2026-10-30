package com.safework.training.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExamStatus {
    DRAFT("草稿"),
    PUBLISHED("进行中"),
    CLOSED("已结束");

    private final String label;
}
