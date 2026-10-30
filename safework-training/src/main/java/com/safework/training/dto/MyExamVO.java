package com.safework.training.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 我的考试列表里的一项 */
@Data
public class MyExamVO {
    private Long examId;
    private String title;
    private String description;
    private Integer questionCount;
    private Integer totalScore;
    private Integer passScore;
    private Integer durationMinutes;
    private Integer maxAttempts;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endTime;
    /** 已考次数 */
    private Integer attempts;
    /** 最高分，没交过卷为 null */
    private Integer bestScore;
    /** 最高分那次的答卷，用于查看成绩 */
    private Long bestRecordId;
    private Boolean passed;
    /** 正在答题的答卷（继续考试） */
    private Long inProgressRecordId;
    /**
     * 状态：NOT_STARTED 未开始 / TODO 待参加 / IN_PROGRESS 答题中 / PASSED 已通过 /
     * RETRY 未通过可补考 / FAILED 未通过（次数用完）/ MISSED 已结束未参加
     */
    private String state;
    private Boolean canStart;
}
