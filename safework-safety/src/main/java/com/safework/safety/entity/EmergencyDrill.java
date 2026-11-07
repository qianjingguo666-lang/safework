package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 应急演练：计划 → 签到 → 登记结果 → 评估打分（发现的问题转隐患整改） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_drill")
public class EmergencyDrill extends BaseEntity {
    private Long planId;
    private String planTitle;
    private String title;
    /** TABLETOP 桌面推演 / LIVE 实战演练 */
    private String drillType;
    private LocalDateTime planTime;
    private String location;
    /** 演练情景（模拟什么事故） */
    private String scenario;
    private String organizerName;
    /** PLANNED 计划中 / DONE 已完成待评估 / EVALUATED 已评估 / CANCELLED 已取消 */
    private String status;
    private LocalDateTime actualTime;
    private Integer participantCount;
    private String summary;
    /** 演练中发现的问题 */
    private String problems;
    private String recorderName;
    /** 评估总分（百分制，按适用项目折算） */
    private java.math.BigDecimal totalScore;
    /** 优秀 / 良好 / 合格 / 不合格 */
    private String grade;
    /** 预案是否适宜：0 时要写修订意见 */
    private Integer planSuitable;
    private String reviseNote;
    private String evaluatorName;
    private LocalDateTime evaluateTime;
}
