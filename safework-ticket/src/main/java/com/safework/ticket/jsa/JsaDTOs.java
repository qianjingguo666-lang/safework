package com.safework.ticket.jsa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** JSA 的请求参数 */
public final class JsaDTOs {

    private JsaDTOs() {
    }

    @Data
    public static class StepIn {
        private String step;
        private String hazards;
        private Integer likelihood;
        private Integer severity;
        private String controls;
    }

    @Data
    public static class Save {
        @NotBlank(message = "请填写 JSA 名称")
        @Size(max = 60, message = "名称不超过 60 字")
        private String title;
        /** 作业类型；不填是通用的 */
        private String ticketType;
        @NotBlank(message = "请填写作业内容")
        @Size(max = 500, message = "作业内容不超过 500 字")
        private String workContent;
        @Size(max = 100, message = "作业地点不超过 100 字")
        private String location;
        @Size(max = 300, message = "劳动防护用品不超过 300 字")
        private String ppe;
        @NotNull(message = "请填写作业步骤")
        private List<StepIn> steps;
        /** 步骤是不是 AI 起草的 */
        private Boolean aiDrafted;
    }

    @Data
    public static class Reject {
        @NotBlank(message = "请写驳回原因")
        @Size(max = 200, message = "驳回原因不超过 200 字")
        private String comment;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String status;
        private String ticketType;
        private String keyword;
    }
}
