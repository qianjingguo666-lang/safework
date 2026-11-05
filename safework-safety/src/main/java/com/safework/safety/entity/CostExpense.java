package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 安全费用支出台账：一笔支出一行；登错了作废（留痕），不物理删除 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cost_expense")
public class CostExpense extends BaseEntity {
    private String expenseNo;
    /** 支出日期所在年度 */
    private Integer year;
    private String category;
    private String title;
    /** 金额（元） */
    private BigDecimal amount;
    private LocalDate expenseDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String deptName;
    /** 收款单位 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String payee;
    /** 财务凭证号 / 发票号 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String voucherNo;
    /** 关联的隐患（隐患整改资金） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long hazardId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String hazardNo;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String hazardTitle;
    private Long recorderId;
    private String recorderName;
    /** 1 有效 / 0 作废 */
    private Integer status;
    private String voidReason;
    private String voiderName;
    private LocalDateTime voidTime;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
