package com.safework.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamSaveDTO {
    @NotBlank(message = "请填写考试名称")
    @Size(max = 100, message = "考试名称不超过 100 字")
    private String title;
    @Size(max = 500, message = "说明不超过 500 字")
    private String description;
    @Size(max = 20, message = "分类不超过 20 字")
    private String category;
    @NotNull @Min(0) @Max(50)
    private Integer singleCount;
    @NotNull @Min(1) @Max(20)
    private Integer singleScore;
    @NotNull @Min(0) @Max(50)
    private Integer multiCount;
    @NotNull @Min(1) @Max(20)
    private Integer multiScore;
    @NotNull @Min(0) @Max(50)
    private Integer judgeCount;
    @NotNull @Min(1) @Max(20)
    private Integer judgeScore;
    @NotNull(message = "请填写及格分")
    @Min(value = 1, message = "及格分至少 1 分")
    private Integer passScore;
    @NotNull(message = "请填写考试时长")
    @Min(value = 5, message = "考试时长至少 5 分钟")
    @Max(value = 240, message = "考试时长不超过 240 分钟")
    private Integer durationMinutes;
    @NotNull(message = "请填写可考次数")
    @Min(value = 1, message = "可考次数至少 1 次")
    @Max(value = 10, message = "可考次数不超过 10 次")
    private Integer maxAttempts;
    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;
    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
    /** 参考部门，为空表示全体员工 */
    private List<Long> deptIds;
    private Boolean showAnswer;
}
