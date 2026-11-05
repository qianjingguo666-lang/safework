package com.safework.safety.ppe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 劳保用品的请求参数 */
public final class PpeDTOs {

    private PpeDTOs() {
    }

    @Data
    public static class ItemSave {
        @NotBlank(message = "请填写用品名称")
        @Size(max = 40, message = "名称不超过 40 字")
        private String name;
        @NotBlank(message = "请选择防护部位")
        private String category;
        @Size(max = 60, message = "规格不超过 60 字")
        private String spec;
        @NotBlank(message = "请填写单位")
        @Size(max = 6, message = "单位不超过 6 字")
        private String unit;
        @NotNull(message = "请填写使用期限")
        private Integer validMonths;
        /** 新建时的初始库存；修改时不改库存（库存只能通过入库、发放变化） */
        @Min(value = 0, message = "库存不能小于 0")
        private Integer stock;
        @NotNull(message = "请填写安全库存")
        @Min(value = 0, message = "安全库存不能小于 0")
        private Integer minStock;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class StockIn {
        @NotNull(message = "请填写入库数量")
        @Min(value = 1, message = "入库数量至少 1")
        @Max(value = 100000, message = "入库数量太大")
        private Integer quantity;
    }

    @Data
    public static class PostSave {
        @NotBlank(message = "请填写岗位名称")
        @Size(max = 40, message = "岗位名称不超过 40 字")
        private String name;
        private List<Long> userIds;
        @Valid
        @NotEmpty(message = "请至少配置一种劳保用品")
        private List<StandardItem> standards;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class StandardItem {
        @NotNull(message = "请选择用品")
        private Long itemId;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量至少 1")
        @Max(value = 20, message = "每次最多发 20 件")
        private Integer quantity;
    }

    @Data
    public static class Issue {
        @NotNull(message = "请选择领用人")
        private Long userId;
        @Valid
        @NotEmpty(message = "请选择要发放的用品")
        private List<StandardItem> items;
        /** 不填就是今天 */
        private LocalDate issueDate;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class LedgerQuery {
        private long current = 1;
        private long size = 20;
        private String keyword;
        /** MISSING / EXPIRED / DUE_SOON / OK；NEED = 未发放 + 已过期 */
        private String state;
        private Long postId;
    }

    @Data
    public static class IssueQuery {
        private long current = 1;
        private long size = 10;
        private String keyword;
        private Long itemId;
        /** IN_USE / REPLACED */
        private String status;
    }
}
