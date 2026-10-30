package com.safework.training.dto;

import com.safework.training.enums.ExamStatus;
import lombok.Data;

@Data
public class ExamQueryDTO {
    private long current = 1;
    private long size = 10;
    private ExamStatus status;
    private String keyword;
}
