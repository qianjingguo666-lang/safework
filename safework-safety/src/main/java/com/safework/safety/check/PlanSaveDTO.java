package com.safework.safety.check;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PlanSaveDTO {
    @NotBlank(message = "请填写计划名称")
    @Size(max = 60, message = "名称不超过 60 字")
    private String name;
    @NotNull(message = "请选择检查表")
    private Long templateId;
    @NotBlank(message = "请选择检查周期")
    private String cycle;
    @NotNull(message = "请选择检查人")
    private Long checkerId;
    @NotBlank(message = "请填写检查区域")
    @Size(max = 100, message = "检查区域不超过 100 字")
    private String location;
    @NotNull(message = "请选择开始日期")
    private LocalDate startDate;
    /** 可选：不填一直执行（一次性检查不填给 7 天） */
    private LocalDate endDate;
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
