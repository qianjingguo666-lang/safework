package com.safework.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HazardReportDTO {
    @NotBlank(message = "隐患标题不能为空")
    @Size(max = 100, message = "标题不超过 100 字")
    private String title;
    @Size(max = 1000, message = "描述不超过 1000 字")
    private String description;
    @NotBlank(message = "请选择隐患类别")
    private String category;
    @NotBlank(message = "请选择隐患等级")
    @Pattern(regexp = "GENERAL|MAJOR", message = "隐患等级只能是 GENERAL、MAJOR")
    private String level;
    @NotBlank(message = "位置不能为空")
    private String location;
    /** 可选：关联受限空间 */
    private Long spaceId;
}
