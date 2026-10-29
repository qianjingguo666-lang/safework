package com.safework.safety.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 隐患处理参数：不同动作用到不同字段 */
@Data
public class HazardActionDTO {
    /** 指派：整改责任人、整改期限 */
    private Long rectifierId;
    private LocalDateTime deadline;
    /** 提交整改：整改措施 */
    private String rectifyMeasure;
    /** 意见（验收不通过、撤销时必填） */
    private String comment;
}
