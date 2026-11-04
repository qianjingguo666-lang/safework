package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 安全检查计划：用哪张检查表、谁来查、在哪查、多久查一次 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_plan")
public class CheckPlan extends BaseEntity {
    private String name;
    private Long templateId;
    private String templateName;
    private String checkType;
    /** DAILY / WEEKLY / MONTHLY / ONCE */
    private String cycle;
    private Long checkerId;
    private String checkerName;
    private String location;
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate endDate;
    /** 下一个要生成任务的周期开始日期；一次性计划生成后为空 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate nextDate;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private String remark;
}
