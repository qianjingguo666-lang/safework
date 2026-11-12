package com.safework.ticket.briefing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class WorkBriefingDTOs {
    private WorkBriefingDTOs() {}

    @Data
    public static class Create {
        @NotNull(message = "请选择已批准的作业票")
        private Long ticketId;
        @Size(max = 30, message = "参与人员不能超过 30 人")
        private List<Long> attendeeIds;
    }

    @Data
    public static class Confirm {
        @NotNull(message = "请手写签名")
        private String signature;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private String status;
        private String keyword;
    }
}