package com.safework.safety.chem;

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

/** 危险化学品的请求参数 */
public final class ChemDTOs {

    private ChemDTOs() {
    }

    @Data
    public static class ItemSave {
        @NotBlank(message = "请填写名称")
        @Size(max = 50, message = "名称不超过 50 字")
        private String name;
        @NotBlank(message = "请填写 CAS 号")
        private String cas;
        @Size(max = 10, message = "UN 编号不超过 10 字")
        private String unNo;
        @NotBlank(message = "请选择危险类别")
        private String category;
        @Size(max = 300, message = "危险特性不超过 300 字")
        private String hazards;
        @DecimalMin(value = "0.001", message = "临界量必须大于 0")
        @DecimalMax(value = "100000", message = "临界量不超过 100000 吨")
        private BigDecimal criticalQty;
        /** 不填按类别取默认值 */
        @DecimalMin(value = "0.1", message = "校正系数不小于 0.1")
        @DecimalMax(value = "50", message = "校正系数不大于 50")
        private BigDecimal beta;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class StoreSave {
        @NotBlank(message = "请填写单元名称")
        @Size(max = 50, message = "名称不超过 50 字")
        private String name;
        @NotBlank(message = "请选择单元类型")
        private String storeType;
        @NotBlank(message = "请填写位置")
        @Size(max = 100, message = "位置不超过 100 字")
        private String location;
        @NotNull(message = "请填写 500 米内常住人数（没有填 0）")
        @Min(value = 0, message = "人数不能是负数")
        @Max(value = 1000000, message = "人数不正确")
        private Integer people;
        @NotNull(message = "请选择负责人")
        private Long managerId;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    /** 单元里存放某种危化品（设计最大储量） */
    @Data
    public static class StockSave {
        @NotNull(message = "请选择危化品")
        private Long chemId;
        @NotNull(message = "请填写设计最大储量")
        @DecimalMin(value = "0.001", message = "最大储量必须大于 0")
        @DecimalMax(value = "1000000", message = "最大储量不超过 1000000 吨")
        private BigDecimal maxQty;
    }

    @Data
    public static class Flow {
        @NotBlank(message = "请选择类型")
        private String flowType;
        @NotNull(message = "请填写数量")
        private BigDecimal qty;
        @Size(max = 100, message = "说明不超过 100 字")
        private String purpose;
    }

    @Data
    public static class FlowQuery {
        private long current = 1;
        private long size = 20;
        private Long storeId;
        private Long chemId;
        private String flowType;
        private LocalDate from;
        private LocalDate to;
    }
}
