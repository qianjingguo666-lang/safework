package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 出入库流水（只增不改） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chem_flow")
public class ChemFlow extends BaseEntity {
    private Long stockId;
    private Long storeId;
    private String storeName;
    private Long chemId;
    private String chemName;
    /** IN 入库 / OUT 出库 / CHECK 盘点 */
    private String flowType;
    /** 入库、出库的数量；盘点时是盘点前后的差（可能是负数） */
    private BigDecimal qty;
    /** 变动后的库存 */
    private BigDecimal balance;
    /** 用途 / 来源 / 盘点说明 */
    private String purpose;
    private Long operatorId;
    private String operatorName;
}
