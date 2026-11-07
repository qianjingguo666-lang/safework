package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 积分流水：只增不改 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("point_record")
public class PointRecord extends BaseEntity {
    private Long userId;
    private String userName;
    /** 变动（正数得分，负数扣减） */
    private Integer points;
    private Integer balanceAfter;
    /** PointRules.SOURCE_TYPES */
    private String sourceType;
    private Long sourceId;
    private String reason;
    private String operatorName;
}
