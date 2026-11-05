package com.safework.safety.accident;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 事故调查的请求参数 */
public final class AccidentDTOs {

    private AccidentDTOs() {
    }

    /** 启动调查 / 修改调查组 */
    @Data
    public static class Team {
        /** 启动调查时必填，修改调查组时不用 */
        private Long eventId;
        @NotNull(message = "请选择调查组长")
        private Long leaderId;
        @Size(max = 200, message = "调查组成员不超过 200 字")
        private String members;
        /** 不填按事故等级自动算（一般事故 30 天，较大及以上 60 天） */
        private LocalDate deadline;
    }

    /** 原因分析 */
    @Data
    public static class Analysis {
        @Size(max = AccidentRules.MAX_CAUSE_LENGTH, message = "直接原因不超过 1000 字")
        private String directCause;
        @Size(max = AccidentRules.MAX_CAUSE_LENGTH, message = "间接原因不超过 1000 字")
        private String indirectCause;
        @Size(max = AccidentRules.MAX_CAUSE_LENGTH, message = "管理原因不超过 1000 字")
        private String managementCause;
        @DecimalMin(value = "0", message = "经济损失不能是负数")
        @DecimalMax(value = "100000", message = "经济损失不超过 100000 万元")
        private BigDecimal economicLoss;
        @Size(max = AccidentRules.MAX_CAUSE_LENGTH, message = "事故教训不超过 1000 字")
        private String lessons;
    }

    /** 警示教育 */
    @Data
    public static class Education {
        @NotNull(message = "请选择教育日期")
        private LocalDate educationDate;
        @NotNull(message = "请填写参加人数")
        @Min(value = 1, message = "参加人数至少 1 人")
        @Max(value = 10000, message = "参加人数不超过 10000")
        private Integer educationCount;
        @Size(max = 500, message = "教育内容不超过 500 字")
        private String educationNote;
    }

    @Data
    public static class MeasureSave {
        @NotBlank(message = "请填写整改措施")
        @Size(max = 300, message = "整改措施不超过 300 字")
        private String content;
        @NotNull(message = "请选择责任人")
        private Long ownerId;
        @NotNull(message = "请选择整改期限")
        private LocalDate deadline;
    }

    @Data
    public static class MeasureDone {
        @NotBlank(message = "请填写落实情况")
        @Size(max = 300, message = "落实情况不超过 300 字")
        private String note;
    }

    @Data
    public static class PunishSave {
        @NotBlank(message = "请填写责任人员")
        @Size(max = 50, message = "姓名不超过 50 字")
        private String personName;
        @NotBlank(message = "请选择责任类别")
        private String duty;
        @NotBlank(message = "请填写处理措施")
        @Size(max = 200, message = "处理措施不超过 200 字")
        private String measure;
    }

    @Data
    public static class Close {
        /** 是否把事故案例写进 AI 知识库 */
        private Boolean toKnowledge;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String keyword;
        /** INVESTIGATING / OVERDUE / CLOSED */
        private String state;
    }
}
