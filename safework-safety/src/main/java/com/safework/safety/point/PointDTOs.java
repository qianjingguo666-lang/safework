package com.safework.safety.point;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 隐患随手拍积分的请求参数 */
public final class PointDTOs {

    private PointDTOs() {
    }

    @Data
    public static class Adjust {
        @NotNull(message = "请选择员工")
        private Long userId;
        @NotNull(message = "请填写调整的积分")
        private Integer points;
        @NotBlank(message = "请填写调整原因")
        @Size(max = 100, message = "原因不超过 100 字")
        private String reason;
    }

    @Data
    public static class RewardSave {
        @NotBlank(message = "请填写奖品名称")
        @Size(max = 40, message = "名称不超过 40 字")
        private String name;
        @NotNull(message = "请填写所需积分")
        @Min(value = 1, message = "所需积分至少 1 分")
        @Max(value = 100000, message = "所需积分不正确")
        private Integer points;
        @NotNull(message = "请填写单价（不花钱填 0）")
        @DecimalMin(value = "0", message = "单价不能是负数")
        @DecimalMax(value = "100000", message = "单价不正确")
        private BigDecimal price;
        @NotNull(message = "请填写库存")
        @Min(value = 0, message = "库存不能是负数")
        @Max(value = 100000, message = "库存不正确")
        private Integer stock;
        @Size(max = 100, message = "说明不超过 100 字")
        private String remark;
    }

    @Data
    public static class Handle {
        /** true 发放 / false 退回 */
        @NotNull(message = "请选择发放还是退回")
        private Boolean issue;
        @Size(max = 100, message = "说明不超过 100 字")
        private String remark;
    }

    @Data
    public static class RecordQuery {
        private long current = 1;
        private long size = 20;
        private Long userId;
        private String sourceType;
    }
}
