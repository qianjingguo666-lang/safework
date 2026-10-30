package com.safework.training.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.safework.training.enums.QuestionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionVO {
    private Long id;
    private QuestionType type;
    private String typeLabel;
    private String category;
    private String content;
    private List<String> options;
    private String answer;
    private String analysis;
    private String source;
    private String sourceDoc;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
