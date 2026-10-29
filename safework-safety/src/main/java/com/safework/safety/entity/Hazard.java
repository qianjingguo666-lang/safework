package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import com.safework.safety.enums.HazardStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 隐患（按租户隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hazard")
public class Hazard extends BaseEntity {
    private String hazardNo;
    private String title;
    private String description;
    /** 设备设施、作业环境、人员行为、安全管理 */
    private String category;
    /** GENERAL 一般隐患 / MAJOR 重大隐患 */
    private String level;
    private String location;
    private Long spaceId;
    private String spaceName;

    private Long reporterId;
    private String reporterName;
    private Long rectifierId;
    private String rectifierName;
    private LocalDateTime deadline;
    private String rectifyMeasure;
    private LocalDateTime rectifyTime;
    private Long verifierId;
    private String verifierName;
    private LocalDateTime verifyTime;

    private HazardStatus status;

    @Version
    private Integer version;

    /** 是否逾期：整改中且已过整改期限（不存库，查询时计算） */
    @TableField(exist = false)
    private Boolean overdue;
}
