package com.safework.safety.emergency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PlanSaveDTO {
    @NotBlank(message = "请填写预案编号")
    @Size(max = 30, message = "预案编号不超过 30 字")
    private String planCode;
    @NotBlank(message = "请填写预案名称")
    @Size(max = 60, message = "预案名称不超过 60 字")
    private String title;
    @NotBlank(message = "请选择预案类型")
    private String planType;
    @NotBlank(message = "请选择适用的事故类型")
    private String accidentType;
    @Size(max = 200, message = "适用范围不超过 200 字")
    private String scope;
    @NotBlank(message = "请填写应急处置程序")
    @Size(max = 5000, message = "处置程序不超过 5000 字")
    private String content;
    /** 应急小组成员 */
    private List<Long> teamUserIds;
}
