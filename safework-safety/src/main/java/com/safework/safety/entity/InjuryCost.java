package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 工伤费用台账：一笔一行 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("injury_cost")
public class InjuryCost extends BaseEntity {
    private Long caseId;
    /** InjuryRules.COST_CATEGORIES */
    private String category;
    private BigDecimal amount;
    /** FUND 工伤保险基金 / COMPANY 单位 */
    private String payer;
    private LocalDate payDate;
    private String remark;
    private String recorderName;
}
