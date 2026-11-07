package com.safework.safety.injury;

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
import java.time.LocalDateTime;

/** 工伤管理的请求参数 */
public final class InjuryDTOs {

    private InjuryDTOs() {
    }

    @Data
    public static class Save {
        @NotBlank(message = "请选择本企业员工还是承包商人员")
        private String employerType;
        /** 本企业员工选账号；承包商人员填姓名 */
        private Long injuredUserId;
        @Size(max = 30, message = "姓名不超过 30 字")
        private String injuredName;
        @Size(max = 60, message = "承包商单位不超过 60 字")
        private String employerName;
        @NotNull(message = "请填写受伤时间")
        private LocalDateTime injuryTime;
        @NotBlank(message = "请填写受伤地点")
        @Size(max = 60, message = "地点不超过 60 字")
        private String location;
        @NotBlank(message = "请填写经过")
        @Size(max = 500, message = "经过不超过 500 字")
        private String description;
        @Size(max = 30, message = "受伤部位不超过 30 字")
        private String injuryPart;
        @Size(max = 30, message = "伤害性质不超过 30 字")
        private String injuryNature;
        /** 关联事故快报（可选） */
        private Long eventId;
    }

    @Data
    public static class Apply {
        @NotNull(message = "请填写申请日期")
        private LocalDate applyDate;
        @Size(max = 40, message = "受理编号不超过 40 字")
        private String applyNo;
    }

    @Data
    public static class Decide {
        @NotNull(message = "请选择认定结果")
        private Boolean recognized;
        @NotNull(message = "请填写认定日期")
        private LocalDate decisionDate;
        @Size(max = 40, message = "认定书编号不超过 40 字")
        private String decisionNo;
        @Size(max = 200, message = "说明不超过 200 字")
        private String note;
    }

    @Data
    public static class Appraise {
        @NotNull(message = "请选择伤残等级（未达到等级选 0）")
        @Min(value = 0, message = "伤残等级不正确")
        @Max(value = 10, message = "伤残等级不正确")
        private Integer grade;
        @NotNull(message = "请填写鉴定日期")
        private LocalDate appraiseDate;
        @DecimalMin(value = "0", message = "工资不能是负数")
        @DecimalMax(value = "1000000", message = "工资不正确")
        private BigDecimal monthlyWage;
    }

    @Data
    public static class Leave {
        @NotNull(message = "请填写开始日期")
        private LocalDate leaveStart;
        @NotNull(message = "请填写结束日期")
        private LocalDate leaveEnd;
        private Boolean extended;
        private LocalDate returnDate;
    }

    @Data
    public static class Close {
        @Size(max = 200, message = "结案说明不超过 200 字")
        private String note;
    }

    @Data
    public static class Cost {
        @NotBlank(message = "请选择费用类别")
        private String category;
        @NotNull(message = "请填写金额")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @DecimalMax(value = "10000000", message = "金额不正确")
        private BigDecimal amount;
        @NotBlank(message = "请选择支付方")
        private String payer;
        @NotNull(message = "请填写支付日期")
        private LocalDate payDate;
        @Size(max = 100, message = "备注不超过 100 字")
        private String remark;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String status;
        private String keyword;
        private Integer year;
    }
}
