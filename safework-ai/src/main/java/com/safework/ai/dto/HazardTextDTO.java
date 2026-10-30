package com.safework.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HazardTextDTO {
    @NotBlank(message = "请先说一句话或输入隐患情况")
    @Size(max = 500, message = "描述不超过 500 字，说重点就行")
    private String text;
}
