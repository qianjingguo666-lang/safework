package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 安全风险研判与承诺：一天一条。第一个人研判时创建，主要负责人签字承诺后公告，系统统计数据在承诺时固定下来 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_commitment")
public class RiskCommitment extends BaseEntity {
    private LocalDate commitDate;
    /** JUDGING 研判中 / COMMITTED 已承诺 */
    private String status;
    /** 生产装置：总数、运行、停产、检修（生产负责人研判时填） */
    private Integer unitsTotal;
    private Integer unitsRunning;
    private Integer unitsStopped;
    private Integer unitsOverhaul;
    /** 是否处于试生产、是否有开停车 */
    private Integer trialRun;
    private Integer startupShutdown;
    private String conditionNote;
    /** 承诺时的系统统计：今天的特殊作业票、重大危险源、超储、未整改隐患、未恢复的临时变更 */
    private Integer hotWorkCount;
    private Integer confinedCount;
    private Integer heightCount;
    private Integer majorSources;
    private Integer overstock;
    private Integer openMajorHazards;
    private Integer openHazards;
    private Integer mocTemporary;
    private Integer abnormalCount;
    private String content;
    private Long committerId;
    private String committerName;
    private LocalDateTime commitTime;
    /** 主要负责人手写签名（列表不查，公告单独查） */
    @TableField(select = false)
    private String signImage;
    private String signHash;
}
