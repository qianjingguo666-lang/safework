package com.safework.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiQuestionDTO {
    @NotNull(message = "请选择制度文件")
    private Long documentId;
    @NotBlank(message = "请填写题目分类")
    @Size(max = 20, message = "分类不超过 20 字")
    private String category;
    /** 出题重点（可选），例如“气体检测”，会优先选取相关段落 */
    @Size(max = 50, message = "出题重点不超过 50 字")
    private String topic;
    @NotNull @Min(0) @Max(10)
    private Integer singleCount;
    @NotNull @Min(0) @Max(10)
    private Integer multiCount;
    @NotNull @Min(0) @Max(10)
    private Integer judgeCount;
}
