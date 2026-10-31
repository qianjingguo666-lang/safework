package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 应急演练：计划 → 完成（记录总结和发现的问题） */
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
    /** PLANNED 计划中 / DONE 已完成 / CANCELLED 已取消 */
    private String status;
    private LocalDateTime actualTime;
    private Integer participantCount;
    private String summary;
    /** 演练中发现的问题 */
    private String problems;
    private String recorderName;
}
