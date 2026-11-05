package com.safework.safety.meeting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 班前会的请求参数 */
public final class MeetingDTOs {

    private MeetingDTOs() {
    }

    @Data
    public static class TeamSave {
        @NotBlank(message = "请填写班组名称")
        @Size(max = 30, message = "名称不超过 30 字")
        private String name;
        @NotNull(message = "请选择班组长")
        private Long leaderId;
        @NotEmpty(message = "请选择班组成员")
        private List<Long> memberIds;
        @Size(max = 200, message = "备注不超过 200 字")
        private String remark;
    }

    @Data
    public static class RollItem {
        @NotNull
        private Long userId;
        @NotBlank(message = "请选择出勤情况")
        private String attend;
        private String bodyState;
        @Size(max = 100, message = "备注不超过 100 字")
        private String note;
    }

    @Data
    public static class Save {
        @NotNull(message = "请选择班组")
        private Long teamId;
        @NotNull(message = "请选择日期")
        private LocalDate meetingDate;
        @NotBlank(message = "请选择班次")
        private String shift;
        private String topics;
        private boolean aiUsed;
        @Valid
        @NotEmpty(message = "请点名")
        private List<RollItem> roll;
    }

    @Data
    public static class Query {
        private long current = 1;
        private long size = 20;
        private Long teamId;
        private LocalDate from;
        private LocalDate to;
        /** true：只看有人状态不好的 */
        private Boolean unfit;
    }
}
