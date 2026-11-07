package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 一次安全生产标准化自评 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("std_assessment")
public class StdAssessment extends BaseEntity {
    private String title;
    private Integer year;
    /** DRAFT 自评中 / DONE 已完成 */
    private String status;
    private Long leaderId;
    /** 自评组长 */
    private String leaderName;
    /** 自评组成员 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String members;
    private LocalDate startDate;
    private LocalDate finishDate;
    /** 完成时的得分（百分制） */
    private Double score;
    private String grade;
    /** 完成时近一年的死亡、重伤人数（安全绩效） */
    private Integer perfDead;
    private Integer perfSerious;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String summary;
}
