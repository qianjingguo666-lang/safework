package com.safework.ticket.resp;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 安全生产责任制的请求参数 */
public final class RespDTOs {

    private RespDTOs() {
    }

    @Data
    public static class Duties {
        private List<String> duties;
    }

    @Data
    public static class BookSave {
        @NotNull(message = "请选择年度")
        private Integer year;
        @NotBlank(message = "请填写标题")
        @Size(max = 60, message = "标题不超过 60 字")
        private String title;
        @NotBlank(message = "请填写通用条款")
        @Size(max = 5000, message = "通用条款不超过 5000 字")
        private String content;
        @NotNull(message = "请选择签署截止日期")
        private LocalDate deadline;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class Sign {
        /** 手写签名（PNG data URL） */
        @NotBlank(message = "请手写签名")
        private String signature;
    }

    @Data
    public static class Assess {
        @NotNull(message = "请填写考核分数")
        @Min(value = 0, message = "分数不能小于 0")
        @Max(value = 100, message = "分数不能大于 100")
        private Integer score;
        @Size(max = 300, message = "考核意见不超过 300 字")
        private String comment;
    }

    @Data
    public static class SignQuery {
        private long current = 1;
        private long size = 20;
        private String keyword;
        /** PENDING / SIGNED */
        private String status;
        /** true：只看还没考核的 */
        private Boolean unassessed;
    }
}
