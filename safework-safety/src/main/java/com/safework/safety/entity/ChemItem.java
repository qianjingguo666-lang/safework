package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 危险化学品目录（MSDS 作为附件上传） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chem_item")
public class ChemItem extends BaseEntity {
    private String name;
    private String cas;
    /** UN 编号 */
    private String unNo;
    private String category;
    /** 危险特性（如“易燃，蒸气与空气可形成爆炸性混合物”） */
    private String hazards;
    /** GB 18218 临界量（吨）；为空表示不在标准里，不参与重大危险源辨识 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private BigDecimal criticalQty;
    /** 校正系数 β */
    private BigDecimal beta;
    /** 1 启用 / 0 停用 */
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
