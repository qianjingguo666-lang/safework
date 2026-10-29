package com.safework.system.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoticeSaveDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多 100 字")
    private String title;
    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容最多 5000 字")
    private String content;
    @Pattern(regexp = "NORMAL|IMPORTANT", message = "级别只能是 NORMAL 或 IMPORTANT")
    private String level = "NORMAL";
}
