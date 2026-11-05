package com.safework.safety.cost;

import jakarta.validation.Valid;
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
import java.util.List;

/** 安全投入的请求参数 */
public final class CostDTOs {

    private CostDTOs() {
    }

    @Data
    public static class PlanSave {
        @NotNull(message = "请选择年度")
        @Min(value = 2000, message = "年度不正确")
        @Max(value = 2100, message = "年度不正确")
        private Integer year;
        @NotNull(message = "请填写上一年度营业收入")
        @DecimalMin(value = "0", message = "营业收入不能是负数")
        @DecimalMax(value = "100000000", message = "营业收入不正确")
        private BigDecimal revenue;
        @NotNull(message = "请填写上年结转（没有填 0）")
        @DecimalMin(value = "0", message = "上年结转不能是负数（上年余额为负应当在上年末补提）")
        @DecimalMax(value = "10000000000", message = "上年结转不正确")
        private BigDecimal carryOver;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
        @Valid
        private List<Budget> budgets;
    }

    @Data
    public static class Budget {
        @NotBlank(message = "请选择类别")
        private String category;
        @NotNull(message = "请填写预算")
        @DecimalMin(value = "0", message = "预算不能是负数")
        @DecimalMax(value = "10000000000", message = "预算不正确")
        private BigDecimal amount;
    }

    @Data
    public static class ExpenseSave {
        @NotBlank(message = "请选择使用类别")
        private String category;
        @NotBlank(message = "请填写支出事项")
        @Size(max = 100, message = "支出事项不超过 100 字")
        private String title;
        @NotNull(message = "请填写金额")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @DecimalMax(value = "1000000000", message = "金额不正确")
        private BigDecimal amount;
        @NotNull(message = "请选择支出日期")
        private LocalDate expenseDate;
        @Size(max = 50, message = "使用部门不超过 50 字")
        private String deptName;
        @Size(max = 100, message = "收款单位不超过 100 字")
        private String payee;
        @Size(max = 50, message = "凭证号不超过 50 字")
        private String voucherNo;
        /** 关联隐患（可选） */
        private Long hazardId;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class VoidReq {
        @NotBlank(message = "请填写作废原因")
        @Size(max = 100, message = "作废原因不超过 100 字")
        private String reason;
    }

    @Data
    public static class ExpenseQuery {
        private long current = 1;
        private long size = 20;
        private Integer year;
        private String category;
        private String keyword;
        /** 1 有效 / 0 作废；不传查全部 */
        private Integer status;
        private Long hazardId;
    }
}
