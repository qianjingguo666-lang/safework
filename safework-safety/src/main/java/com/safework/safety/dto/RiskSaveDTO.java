package com.safework.safety.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RiskSaveDTO {
    @NotBlank(message = "请填写风险点编号")
    @Size(max = 20, message = "编号不超过 20 字")
    private String pointCode;
    @NotBlank(message = "请填写风险点名称")
    @Size(max = 50, message = "名称不超过 50 字")
    private String pointName;
    @NotBlank(message = "请选择类型")
    private String pointType;
    @NotBlank(message = "请填写位置")
    @Size(max = 100, message = "位置不超过 100 字")
    private String location;
    private Long deptId;
    @NotNull(message = "请选择责任人")
    private Long responsibleId;
    private Long spaceId;
    @Size(max = 500, message = "危险有害因素不超过 500 字")
    private String hazardFactors;
    @Size(max = 200, message = "可能导致的事故不超过 200 字")
    private String possibleAccidents;
    @NotNull(message = "请选择 L（可能性）")
    private Double lecL;
    @NotNull(message = "请选择 E（暴露频繁程度）")
    private Double lecE;
    @NotNull(message = "请选择 C（后果）")
    private Double lecC;
    @NotBlank(message = "请填写管控措施")
    @Size(max = 2000, message = "管控措施不超过 2000 字")
    private String controlMeasures;
    /** 不填按风险等级取默认值（重大 1 天、较大 7 天、一般 15 天、低 30 天） */
    @Min(value = 1, message = "巡检周期至少 1 天")
    @Max(value = 365, message = "巡检周期不超过 365 天")
    private Integer checkCycleDays;
}
