package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.safety.enums.RiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 风险点（风险单元）：一台设备、一项作业活动或一个区域，经 LEC 评估定级，明确管控措施和责任人 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_point")
public class RiskPoint extends BaseEntity {
    private String pointCode;
    private String pointName;
    /** 设备设施 / 作业活动 / 区域场所 */
    private String pointType;
    private String location;
    private Long deptId;
    private Long responsibleId;
    private String responsibleName;
    private Long spaceId;
    private String spaceName;
    /** 危险有害因素 */
    private String hazardFactors;
    /** 可能导致的事故类型，如“中毒窒息、火灾爆炸” */
    private String possibleAccidents;
    private Double lecL;
    private Double lecE;
    private Double lecC;
    /** D = L × E × C */
    private Double lecD;
    private RiskLevel riskLevel;
    /** 管控措施，一行一条 */
    private String controlMeasures;
    private Integer checkCycleDays;
    private LocalDateTime lastCheckTime;
    private LocalDateTime nextCheckTime;
    private Integer status;

    @TableField(exist = false)
    private String deptName;
    /** OVERDUE 逾期 / TODAY 今天到期 / UPCOMING */
    @TableField(exist = false)
    private String checkState;
}
