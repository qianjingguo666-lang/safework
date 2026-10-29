package com.safework.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 当前登录用户所属企业的信息，显示在页面顶部 */
@Data
public class TenantInfoVO {
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private boolean platform;
    private String packageName;
    private LocalDateTime expireTime;
    /** 距离到期还有几天，为空表示不限期 */
    private Long daysLeft;
    private Integer accountLimit;
}
