package com.safework.safety.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 职业健康的请求参数 */
public final class HealthDTOs {

    private HealthDTOs() {
    }

    @Data
    public static class FactorSave {
        @NotBlank(message = "请填写危害因素名称")
        @Size(max = 40, message = "名称不超过 40 字")
        private String name;
        @NotBlank(message = "请选择类别")
        private String category;
        @NotNull(message = "请填写体检周期")
        @Min(value = 3, message = "体检周期至少 3 个月")
        @Max(value = 36, message = "体检周期最多 36 个月")
        private Integer cycleMonths;
        @Size(max = 60, message = "接触限值不超过 60 字")
        private String limitValue;
        @Size(max = 300, message = "体检项目不超过 300 字")
        private String examItems;
        @Size(max = 300, message = "职业禁忌证不超过 300 字")
        private String contraindication;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class PostFactors {
        private List<Long> factorIds;
    }

    @Data
    public static class ExamSave {
        @NotNull(message = "请选择体检人员")
        private Long userId;
        @NotBlank(message = "请选择体检类型")
        private String examType;
        @NotNull(message = "请填写体检日期")
        private LocalDate examDate;
        @NotBlank(message = "请选择体检结论")
        private String conclusion;
        @Size(max = 60, message = "体检机构不超过 60 字")
        private String org;
        @Size(max = 40, message = "报告编号不超过 40 字")
        private String reportNo;
        @Size(max = 500, message = "说明不超过 500 字")
        private String detail;
        /** 需要复查时的复查日期 */
        private LocalDate recheckDate;
    }

    @Data
    public static class Handle {
        @NotBlank(message = "请填写处理措施")
        @Size(max = 300, message = "处理措施不超过 300 字")
        private String measure;
    }

    @Data
    public static class LedgerQuery {
        private long current = 1;
        private long size = 20;
        private String keyword;
        /** NEVER / OVERDUE / DUE_SOON / OK / NEED（从没体检 + 逾期）/ ABNORMAL（有未处理的异常） */
        private String state;
        private Long postId;
    }

    @Data
    public static class ExamQuery {
        private long current = 1;
        private long size = 10;
        private String keyword;
        private String conclusion;
        private String examType;
        /** true：只看待处理的异常 */
        private Boolean open;
        private Long userId;
    }
}
