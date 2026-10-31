package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 承包商违章记录：扣分累计到安全评分 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contractor_violation")
public class ContractorViolation extends BaseEntity {
    private Long contractorId;
    private Long workerId;
    private String workerName;
    private String description;
    /** 扣分 1 ~ 20 */
    private Integer points;
    private LocalDateTime occurTime;
    private String recorderName;
}
