package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 事故责任人员处理 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("accident_punish")
public class AccidentPunish extends BaseEntity {
    private Long investigationId;
    private String personName;
    /** DIRECT 直接责任 / MAIN 主要责任 / LEADER 领导责任 / MANAGEMENT 管理责任 */
    private String duty;
    /** 处理措施：如通报批评、经济处罚、停岗培训 */
    private String measure;
    private String recorderName;
}
