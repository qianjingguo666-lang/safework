package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 单元里存放的某种危化品：当前库存 + 设计最大储量（重大危险源辨识按最大储量算） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chem_stock")
public class ChemStock extends BaseEntity {
    private Long storeId;
    private Long chemId;
    /** 当前库存（吨），只通过出入库改变 */
    private BigDecimal qty;
    /** 设计最大储量（吨） */
    private BigDecimal maxQty;
}
