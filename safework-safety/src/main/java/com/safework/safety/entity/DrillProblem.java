package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 演练发现的问题：可以转隐患走整改闭环 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_drill_problem")
public class DrillProblem extends BaseEntity {
    private Long drillId;
    /** 见 DrillRules.PROBLEM_CATEGORIES */
    private String category;
    private String description;
    private Long hazardId;
    private String hazardNo;
}
