package com.safework.training.dto;

import lombok.Data;

import java.util.Map;

/** 考生答案：题号（从 1 开始）→ 选项字母，多选题如 "AC" */
@Data
public class AnswersDTO {
    private Map<Integer, String> answers;
}
