package com.safework.system.dto.platform;

import lombok.Data;

import java.util.List;

/** 平台首页统计 */
@Data
public class PlatformStatsVO {
    private long tenantTotal;
    private long tenantEnabled;
    /** 30 天内到期（未过期） */
    private long tenantExpiringSoon;
    private long tenantExpired;
    private long userTotal;
    private int onlineTotal;
    private long loginSuccessToday;
    private long loginFailToday;
    /** 即将到期、已到期的企业，提醒运营续费 */
    private List<TenantVO> expiringTenants;
}
