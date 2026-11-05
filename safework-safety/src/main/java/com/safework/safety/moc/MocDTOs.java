package com.safework.safety.moc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 变更管理的请求参数 */
public final class MocDTOs {

    private MocDTOs() {
    }

    @Data
    public static class Save {
        @NotBlank(message = "请填写变更名称")
        @Size(max = 60, message = "名称不超过 60 字")
        private String title;
        @NotBlank(message = "请选择变更类型")
        private String changeType;
        private boolean temporary;
        private LocalDate restoreDate;
        @NotBlank(message = "请填写变更地点 / 装置")
        @Size(max = 100, message = "地点不超过 100 字")
        private String location;
        @NotBlank(message = "请填写变更原因")
        @Size(max = 500, message = "变更原因不超过 500 字")
        private String reason;
        @NotBlank(message = "请填写变更内容")
        @Size(max = 2000, message = "变更内容不超过 2000 字")
        private String content;
    }

    /** 风险评估：LEC + 风险分析 + 控制措施 */
    @Data
    public static class Assess {
        @NotNull(message = "请选择 L")
        private Double lecL;
        @NotNull(message = "请选择 E")
        private Double lecE;
        @NotNull(message = "请选择 C")
        private Double lecC;
        @NotBlank(message = "请填写风险分析")
        @Size(max = 1000, message = "风险分析不超过 1000 字")
        private String riskAnalysis;
        @NotBlank(message = "请填写控制措施")
        @Size(max = 1000, message = "控制措施不超过 1000 字")
        private String measures;
    }

    /** 审批、驳回、退回、实施完成、恢复：意见 / 说明 */
    @Data
    public static class Comment {
        @Size(max = 500, message = "不超过 500 字")
        private String comment;
    }

    @Data
    public static class Pssr {
        /** 每一项：true 合格 / false 不合格 */
        private List<Boolean> results;
        private List<String> notes;
        @Size(max = 300, message = "检查意见不超过 300 字")
        private String comment;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String keyword;
        private String status;
        private String changeType;
        /** true：只看要我处理的 */
        private Boolean todo;
    }
}
