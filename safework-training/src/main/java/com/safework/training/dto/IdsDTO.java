package com.safework.training.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class IdsDTO {
    @NotEmpty(message = "请选择题目")
    @Size(max = 200, message = "一次最多处理 200 条")
    private List<Long> ids;
}
