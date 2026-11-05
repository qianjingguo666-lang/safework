package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 年度安全生产费用计划：上年营业收入 → 本年应提（超额累退），上年结转 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_plan")
public class CostPlan extends BaseEntity {
    private Integer year;
    /** 上一年度营业收入（万元） */
    private BigDecimal revenue;
    /** 本年应提（元），保存时按 CostRules.accrue 计算 */
    private BigDecimal accrueAmount;
    /** 上年结转（元） */
    private BigDecimal carryOver;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
    private String updaterName;
}
