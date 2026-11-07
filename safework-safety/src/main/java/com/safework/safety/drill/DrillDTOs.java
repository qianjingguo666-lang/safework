package com.safework.safety.drill;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 应急演练的请求参数 */
public final class DrillDTOs {

    private DrillDTOs() {
    }

    @Data
    public static class Save {
        @NotNull(message = "请选择演练的预案")
        private Long planId;
        @NotBlank(message = "请填写演练名称")
        @Size(max = 60, message = "演练名称不超过 60 字")
        private String title;
        /** TABLETOP 桌面推演 / LIVE 实战演练 */
        @NotBlank(message = "请选择演练形式")
        private String drillType;
        @NotNull(message = "请选择计划时间")
        private LocalDateTime planTime;
        @Size(max = 100, message = "地点不超过 100 字")
        private String location;
        @Size(max = 500, message = "演练情景不超过 500 字")
        private String scenario;
        /** 参演人员；不填时只通知预案的应急小组 */
        @Valid
        @Size(max = 100, message = "参演人员不超过 100 人")
        private List<Participant> participants;
    }

    @Data
    public static class Participant {
        @NotNull(message = "请选择参演人员")
        private Long userId;
        @NotBlank(message = "请选择参演角色")
        private String role;
    }

    @Data
    public static class Complete {
        @NotNull(message = "请填写实际演练时间")
        private LocalDateTime actualTime;
        @NotNull(message = "请填写参演人数")
        @Min(value = 1, message = "参演人数至少 1 人")
        private Integer participantCount;
        @NotBlank(message = "请填写演练总结")
        @Size(max = 2000, message = "总结不超过 2000 字")
        private String summary;
        @Size(max = 2000, message = "问题不超过 2000 字")
        private String problems;
        /** 实际参加的参演人员（账号 ID）；不填时按已签到的算 */
        private List<Long> attendedUserIds;
    }

    @Data
    public static class ScoreIn {
        @NotBlank(message = "评估项目不正确")
        private String code;
        /** null 表示不适用 */
        @DecimalMin(value = "0", message = "分数不能是负数")
        @DecimalMax(value = "100", message = "分数不正确")
        private BigDecimal score;
        @Size(max = 200, message = "评语不超过 200 字")
        private String remark;
    }

    @Data
    public static class ProblemIn {
        @NotBlank(message = "请选择问题类别")
        private String category;
        @NotBlank(message = "请填写问题描述")
        @Size(max = 300, message = "问题描述不超过 300 字")
        private String description;
    }

    @Data
    public static class Evaluate {
        @Valid
        @NotNull(message = "请给评估项目打分")
        private List<ScoreIn> scores;
        @NotNull(message = "请选择预案是否适宜")
        private Boolean planSuitable;
        @Size(max = 300, message = "修订意见不超过 300 字")
        private String reviseNote;
        @Valid
        @Size(max = 20, message = "问题不超过 20 条")
        private List<ProblemIn> problems;
    }

    @Data
    public static class Query {
        private Long planId;
        private Integer year;
        private String status;
    }
}
