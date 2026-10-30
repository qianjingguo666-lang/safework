package com.safework.training.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.training.enums.ExamStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 考试：按规则随机组卷（每种题型抽几道、每道几分），每个人的试卷都不一样 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("train_exam")
public class TrainExam extends BaseEntity {
    private String title;
    private String description;
    /** 只从这个分类里抽题，为空表示全部分类 */
    private String category;
    private Integer singleCount;
    private Integer singleScore;
    private Integer multiCount;
    private Integer multiScore;
    private Integer judgeCount;
    private Integer judgeScore;
    private Integer totalScore;
    private Integer passScore;
    private Integer durationMinutes;
    /** 每人最多考几次（通过后不能再考） */
    private Integer maxAttempts;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 选择的参考部门 ID（逗号分隔），为空表示全体员工 */
    private String deptIds;
    private String deptNames;
    /** 参考部门及其所有下级部门（保存时展开），查询“我的考试”时用 FIND_IN_SET 匹配 */
    private String targetDeptIds;
    /** 交卷后是否显示正确答案和解析：1 显示 0 只显示分数 */
    private Integer showAnswer;
    private ExamStatus status;
    private LocalDateTime publishTime;
    private String creatorName;

    /** 列表统计：已交卷人数、通过人数 */
    @TableField(exist = false)
    private Long submittedUsers;
    @TableField(exist = false)
    private Long passedUsers;
}
