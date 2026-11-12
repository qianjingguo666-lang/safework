package com.safework.ticket.plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public final class JobPlanDTOs {
    private JobPlanDTOs() {}

    @Data
    public static class Save {
        @NotBlank(message = "请填写作业名称")
        @Size(max = 100, message = "作业名称不超过 100 字")
        private String title;
        @NotBlank(message = "请选择作业类型")
        private String ticketType;
        @NotBlank(message = "请填写作业地点")
        @Size(max = 100, message = "作业地点不超过 100 字")
        private String location;
        @NotBlank(message = "请填写作业内容")
        @Size(max = 500, message = "作业内容不超过 500 字")
        private String workContent;
        @Size(max = 60, message = "部门/班组不超过 60 字")
        private String departmentName;
        @NotNull(message = "请选择作业负责人")
        private Long responsibleId;
        @NotNull(message = "请选择计划开始时间")
        private LocalDateTime plannedStartTime;
        @NotNull(message = "请选择计划结束时间")
        private LocalDateTime plannedEndTime;
        private Long ticketId;
        @Size(max = 500, message = "备注不超过 500 字")
        private String remark;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String status;
        private String ticketType;
        private String keyword;
    }
}