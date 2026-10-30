package com.safework.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperLogQueryDTO {
    private long current = 1;
    private long size = 20;
    private String module;
    private String operatorName;
    private Integer status;
    /** 按错误编号查：用户反馈“错误编号 xxx”时直接搜 */
    private String traceId;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
