package com.safework.safety.check;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TemplateSaveDTO {
    @NotBlank(message = "请填写检查表名称")
    @Size(max = 60, message = "名称不超过 60 字")
    private String name;
    @NotBlank(message = "请选择检查类型")
    private String checkType;
    @NotEmpty(message = "检查表至少要有一个检查项")
    private List<CheckItem> items;
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
