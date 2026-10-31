package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 突发事件 / 事故快报（按租户隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_event")
public class EmergencyEvent extends BaseEntity {
    private String eventNo;
    private String title;
    private String accidentType;
    /** GENERAL 一般 / LARGER 较大 / MAJOR 重大 / EXTRA 特别重大 */
    private String level;
    private String location;
    private Long spaceId;
    private String spaceName;
    private String description;
    /** 死亡人数 */
    private Integer dead;
    /** 重伤人数 */
    private Integer seriousInjured;
    /** 轻伤人数 */
    private Integer minorInjured;
    private LocalDateTime occurTime;
    private Long reporterId;
    private String reporterName;
    /** REPORTED 已上报 / RESPONDING 响应中 / CLOSED 已结束 */
    private String status;
    private Long planId;
    private String planTitle;
    /** 启动响应的人（现场指挥） */
    private Long commanderId;
    private String commanderName;
    private LocalDateTime responseTime;
    private LocalDateTime closeTime;
    /** 事后总结（结束时必填） */
    private String summary;

    @Version
    private Integer version;
}
