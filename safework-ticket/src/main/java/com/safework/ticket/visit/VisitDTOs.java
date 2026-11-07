package com.safework.ticket.visit;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 外来人员入厂的请求参数 */
public final class VisitDTOs {

    private VisitDTOs() {
    }

    @Data
    public static class Create {
        @NotBlank(message = "请选择类型")
        private String visitType;
        @NotBlank(message = "请填写来访人（车辆填驾驶员）姓名")
        @Size(max = 30, message = "姓名不超过 30 字")
        private String visitorName;
        @NotBlank(message = "请填写手机号")
        private String phone;
        /** 完整身份证号：只用来校验，保存打码后的 */
        @NotBlank(message = "请填写身份证号")
        private String idCard;
        @NotBlank(message = "请填写单位")
        @Size(max = 60, message = "单位不超过 60 字")
        private String company;
        @NotBlank(message = "请填写来访事由")
        @Size(max = 100, message = "来访事由不超过 100 字")
        private String purpose;
        @NotNull(message = "请填写人数")
        @Min(value = 1, message = "人数至少 1 人")
        @Max(value = 50, message = "人数不超过 50 人")
        private Integer peopleCount;
        @Size(max = 10, message = "车牌号不正确")
        private String plateNo;
        /** 接待人，不填就是自己 */
        private Long hostId;
        @NotNull(message = "请选择来访日期")
        private LocalDate planDate;
        @Size(max = 30, message = "驾驶员资格证号不超过 30 字")
        private String driverCertNo;
        private LocalDate driverCertExpire;
        @Size(max = 30, message = "押运员姓名不超过 30 字")
        private String escortName;
        @Size(max = 30, message = "押运员资格证号不超过 30 字")
        private String escortCertNo;
        private LocalDate escortCertExpire;
        @Size(max = 40, message = "道路运输证号不超过 40 字")
        private String transportPermitNo;
        private LocalDate transportPermitExpire;
        @Size(max = 40, message = "货物名称不超过 40 字")
        private String cargoName;
        @Size(max = 10, message = "UN 编号不超过 10 字")
        private String cargoUnNo;
        @DecimalMin(value = "0", message = "数量不能是负数")
        @DecimalMax(value = "100", message = "单车数量不超过 100 吨")
        private Double cargoQty;
        @Size(max = 40, message = "卸货地点不超过 40 字")
        private String destination;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class Reason {
        @NotBlank(message = "请填写原因")
        @Size(max = 100, message = "原因不超过 100 字")
        private String reason;
    }

    @Data
    public static class Enter {
        /** 入厂安全须知手写签名（PNG data URL） */
        @NotBlank(message = "请让来访人阅读入厂安全须知并手写签名")
        private String signImage;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private LocalDate date;
        private String status;
        private String visitType;
        private String keyword;
    }
}
