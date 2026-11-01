package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 设备设施：特种设备、安全附件、检测报警仪器、消防设施 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment")
public class Equipment extends BaseEntity {
    /** 设备位号，如 R-201、SV-201 */
    private String equipCode;
    private String name;
    /** 大类：SPECIAL / ACCESSORY / DETECTOR / FIRE / OTHER（由设备类型决定） */
    private String category;
    /** 设备类型，见 EquipmentRules.TYPES */
    private String equipType;
    private String location;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long responsibleId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String responsibleName;
    /** 关联的风险点（可选） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long riskPointId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String riskPointName;
    /** 特种设备使用登记证编号 */
    private String registerNo;
    private String manufacturer;
    private String model;
    private LocalDate installDate;
    /** 检验周期（月） */
    private Integer cycleMonths;
    private LocalDate lastInspectDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate nextInspectDate;
    /** 最近一次检验结论 PASS / FAIL */
    private String lastResult;
    /** IN_USE / STOPPED / SCRAPPED */
    private String status;
    private String statusReason;
    private String remark;

    /** NEVER / OVERDUE / DUE_SOON / OK / NOT_IN_USE，查询时计算 */
    @TableField(exist = false)
    private String inspectState;
    @TableField(exist = false)
    private Long daysLeft;
}
