package com.safework.safety.observation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

public final class SafetyObservationDTOs {
    private SafetyObservationDTOs() {
    }

    @Data
    public static class Report {
        @NotBlank @Size(max = 24)
        private String observationType;
        @NotBlank @Size(max = 100)
        private String title;
        @NotNull
        private LocalDateTime occurTime;
        @NotBlank @Size(max = 100)
        private String location;
        @NotBlank @Size(max = 1000)
        private String description;
        @NotBlank @Size(max = 10)
        private String potentialLevel;
        @Size(max = 500)
        private String immediateAction;
    }

    @Data
    public static class Analysis {
        @NotBlank @Size(max = 1000)
        private String rootCause;
        @NotBlank @Size(max = 1000)
        private String correctiveAction;
        /** 将险情同步转成隐患闭环；仅限未遂事件和不安全行为 / 状态。 */
        private Boolean toHazard;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String keyword;
        private String status;
        private String observationType;
    }
}
