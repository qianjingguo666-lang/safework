package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 事故调查：一起事件（事故快报）一份，按“四不放过”结案 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("accident_investigation")
public class AccidentInvestigation extends BaseEntity {
    private Long eventId;
    /** 以下几项是启动调查时从事故快报复制的快照 */
    private String eventNo;
    private String eventTitle;
    private String accidentType;
    private String level;
    private LocalDateTime occurTime;

    /** INVESTIGATING 调查中 / CLOSED 已结案（超期是查询时算的） */
    private String status;
    /** 调查组长：除了管理员，组长也能填写调查内容 */
    private Long leaderId;
    private String leaderName;
    /** 调查组成员（文字） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String members;
    private LocalDate deadline;

    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String directCause;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String indirectCause;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String managementCause;
    /** 直接经济损失（万元） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal economicLoss;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String lessons;

    /** 警示教育 */
    private LocalDate educationDate;
    private Integer educationCount;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String educationNote;

    private String starterName;
    private LocalDateTime closeTime;
    private String closerName;
    /** 结案时是否写进 AI 知识库 */
    private Integer toKnowledge;

    @Version
    private Integer version;

    /** 查询时计算：INVESTIGATING / OVERDUE / CLOSED */
    @TableField(exist = false)
    private String state;
}
