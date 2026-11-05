package com.safework.ticket.kpi;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 安全目标的请求参数 */
public final class KpiDTOs {

    private KpiDTOs() {
    }

    @Data
    public static class TargetItem {
        @NotNull
        private String code;
        @NotNull(message = "请填写目标值")
        private Double target;
        @NotNull(message = "请填写权重")
        private Integer weight;
    }

    @Data
    public static class Targets {
        @NotNull(message = "请选择年度")
        private Integer year;
        @NotEmpty(message = "请填写目标")
        private List<TargetItem> items;
    }
}
