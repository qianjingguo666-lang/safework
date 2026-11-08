package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 作业安全分析（JSA）：把一项作业拆成步骤，逐步识别危害、评估风险、定控制措施；审核发布后开作业票时引用 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jsa")
public class Jsa extends BaseEntity {
    private String jsaNo;
    private String title;
    /** 适用的作业类型（TicketType 名称）；null 表示通用 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String ticketType;
    private String workContent;
    private String location;
    /** 劳动防护用品 */
    private String ppe;
    /** 步骤 JSON：[{step, hazards, likelihood, severity, controls}] */
    private String steps;
    private Integer stepCount;
    private Integer maxRisk;
    /** 是否用 AI 起草过 */
    private Integer aiDrafted;
    private String status;
    private Long authorId;
    private String authorName;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String reviewComment;
    private LocalDateTime publishTime;
    /** 复制来源（复审时复制一份） */
    private Long copiedFrom;
}
