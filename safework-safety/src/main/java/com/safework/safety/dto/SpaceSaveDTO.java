package com.safework.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpaceSaveDTO {
    @NotBlank(message = "空间编号不能为空")
    @Size(max = 32, message = "空间编号不超过 32 位")
    private String spaceCode;
    @NotBlank(message = "空间名称不能为空")
    private String spaceName;
    @NotBlank(message = "空间类型不能为空")
    private String spaceType;
    @NotBlank(message = "位置不能为空")
    private String location;
    private Long deptId;
    private Long responsibleId;
    private String medium;
    @NotBlank(message = "风险等级不能为空")
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "风险等级只能是 HIGH、MEDIUM、LOW")
    private String riskLevel;
    private String remark;
}
