package com.safework.ticket.std;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 标准化自评的请求参数 */
public final class StdDTOs {

    private StdDTOs() {
    }

    @Data
    public static class Create {
        @NotBlank(message = "请填写自评名称")
        @Size(max = 60, message = "名称不超过 60 字")
        private String title;
        @Size(max = 200, message = "自评组成员不超过 200 字")
        private String members;
    }

    @Data
    public static class ItemSave {
        @NotNull(message = "请选择是否空项")
        private Boolean na;
        @NotNull(message = "请填写扣分")
        @DecimalMin(value = "0", message = "扣分不能是负数")
        @DecimalMax(value = "100", message = "扣分不正确")
        private Double deduct;
        @Size(max = 300, message = "扣分说明不超过 300 字")
        private String note;
    }

    @Data
    public static class Finish {
        @Size(max = 500, message = "自评结论不超过 500 字")
        private String summary;
    }
}
