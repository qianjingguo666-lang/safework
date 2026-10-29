package com.safework.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogQueryDTO {
    private long current = 1;
    private long size = 20;
    /** 只有平台查询全部企业时使用 */
    private String tenantCode;
    private String username;
    private String ip;
    private Integer status;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
