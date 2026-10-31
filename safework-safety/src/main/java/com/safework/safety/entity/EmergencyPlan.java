package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 应急预案（按租户隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_plan")
public class EmergencyPlan extends BaseEntity {
    private String planCode;
    private String title;
    /** COMPREHENSIVE 综合 / SPECIAL 专项 / ONSITE 现场处置方案 */
    private String planType;
    /** 适用的事故类型，见 EmergencyRules.ACCIDENT_TYPES */
    private String accidentType;
    /** 适用范围（区域、装置、作业） */
    private String scope;
    /** 应急处置程序（按步骤写，一行一步） */
    private String content;
    /** 应急小组成员 ID，逗号分隔；启动响应时通知这些人 */
    private String teamUserIds;
    private String teamNames;
    /** 1 启用 0 停用 */
    private Integer status;
    private LocalDateTime lastDrillTime;
    private String updaterName;

    /** 演练状态（不存库） */
    @TableField(exist = false)
    private String drillState;
    @TableField(exist = false)
    private LocalDate nextDrillDue;
}
