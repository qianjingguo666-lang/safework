package com.safework.training.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RecordStatus {
    IN_PROGRESS("答题中"),
    SUBMITTED("已交卷");

    private final String label;
}
