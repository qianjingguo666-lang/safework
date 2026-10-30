package com.safework.training.dto;

import com.safework.training.enums.QuestionType;
import lombok.Data;

@Data
public class QuestionQueryDTO {
    private long current = 1;
    private long size = 10;
    private QuestionType type;
    private String category;
    private Integer status;
    private String source;
    private String keyword;
}
