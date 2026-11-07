package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 可兑换的奖品 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("point_reward")
public class PointReward extends BaseEntity {
    private String name;
    /** 需要多少积分 */
    private Integer points;
    /** 单价（元），发放时记入安全投入；0 表示不花钱（如表彰） */
    private BigDecimal price;
    private Integer stock;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
