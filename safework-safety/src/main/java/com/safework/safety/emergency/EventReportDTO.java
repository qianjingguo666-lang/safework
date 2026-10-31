package com.safework.safety.emergency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 事故快报：先报告、后完善，必填项尽量少 */
@Data
public class EventReportDTO {
    @NotBlank(message = "请用一句话说明发生了什么")
    @Size(max = 100, message = "标题不超过 100 字")
    private String title;
    @NotBlank(message = "请选择事故类型")
    private String accidentType;
    @NotBlank(message = "请选择事故等级")
    private String level;
    @NotBlank(message = "请填写事发地点")
    @Size(max = 100, message = "地点不超过 100 字")
    private String location;
    private Long spaceId;
    @Size(max = 1000, message = "情况描述不超过 1000 字")
    private String description;
    @Min(value = 0, message = "人数不能为负数")
    @Max(value = 9999, message = "人数不正确")
    private Integer dead = 0;
    @Min(value = 0, message = "人数不能为负数")
    @Max(value = 9999, message = "人数不正确")
    private Integer seriousInjured = 0;
    @Min(value = 0, message = "人数不能为负数")
    @Max(value = 9999, message = "人数不正确")
    private Integer minorInjured = 0;
    @NotNull(message = "请填写事发时间")
    private LocalDateTime occurTime;
}
