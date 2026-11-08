package com.safework.ticket.commit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 风险研判承诺的请求参数 */
public final class CommitDTOs {

    private CommitDTOs() {
    }

    @Data
    public static class ResultIn {
        @NotBlank(message = "研判项目不正确")
        private String code;
        @NotNull(message = "请逐项选择正常还是异常")
        private Boolean ok;
        @Size(max = 200, message = "异常情况不超过 200 字")
        private String note;
        @Size(max = 200, message = "管控措施不超过 200 字")
        private String measure;
    }

    @Data
    public static class Judge {
        @NotBlank(message = "请选择研判方面")
        private String aspect;
        @Valid
        @NotNull(message = "请逐项研判")
        private List<ResultIn> results;
        /** 以下只有生产负责人研判时填 */
        private Integer unitsTotal;
        private Integer unitsRunning;
        private Integer unitsStopped;
        private Integer unitsOverhaul;
        private Boolean trialRun;
        private Boolean startupShutdown;
        @Size(max = 200, message = "说明不超过 200 字")
        private String conditionNote;
    }

    @Data
    public static class Commit {
        @NotBlank(message = "请主要负责人手写签名")
        private String signImage;
    }
}
