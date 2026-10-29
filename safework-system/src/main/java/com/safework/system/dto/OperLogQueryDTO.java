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
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
