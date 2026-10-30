package com.safework.training.dto;

import com.safework.training.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuestionSaveDTO {
    @NotNull(message = "请选择题型")
    private QuestionType type;
    @NotBlank(message = "请填写分类")
    @Size(max = 20, message = "分类不超过 20 字")
    private String category;
    @NotBlank(message = "请填写题干")
    @Size(max = 500, message = "题干不超过 500 字")
    private String content;
    /** 判断题可以不传，固定为【正确、错误】 */
    private List<String> options;
    @NotBlank(message = "请设置正确答案")
    private String answer;
    @Size(max = 500, message = "解析不超过 500 字")
    private String analysis;
}
