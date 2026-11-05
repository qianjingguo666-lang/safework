package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 年度计划里一个使用类别的预算（元） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_budget")
public class CostBudget extends BaseEntity {
    private Long planId;
    private Integer year;
    /** CostRules.CATEGORIES */
    private String category;
    private BigDecimal amount;
}
