package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 演练评估打分：一个评估项目一行，score 为 null 表示不适用 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_drill_score")
public class DrillScore extends BaseEntity {
    private Long drillId;
    private String itemCode;
    private BigDecimal score;
    private String remark;
}
