package com.safework.system.dto.platform;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVO {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    private Long packageId;
    private String packageName;
    private Integer accountLimit;
    private long userCount;
    private int onlineCount;
    private Integer status;
    private LocalDateTime expireTime;
    /** 剩余天数，负数表示已过期几天，为空表示不限期 */
    private Long daysLeft;
    private String remark;
    private LocalDateTime createTime;
}
