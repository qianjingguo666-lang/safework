package com.safework.training.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.training.enums.RecordStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 一次答卷（每人每次考试一条，第几次考由 attemptNo 区分） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("train_exam_record")
public class TrainExamRecord extends BaseEntity {
    private Long examId;
    private String examTitle;
    private Long userId;
    private String userName;
    private Long deptId;
    private String deptName;
    private Integer attemptNo;
    /** 试卷快照（JSON，含正确答案，绝不能原样返回给考生） */
    private String paper;
    /** 考生答案（JSON，题号 → 答案字母） */
    private String answers;
    private Integer score;
    private Integer totalScore;
    private Integer passed;
    private RecordStatus status;
    private LocalDateTime startTime;
    /** 交卷截止时间 = min(开始时间 + 考试时长, 考试结束时间) */
    private LocalDateTime deadline;
    private LocalDateTime submitTime;
    /** 1 表示时间到了由系统自动交卷 */
    private Integer autoSubmit;
}
