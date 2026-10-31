package com.safework.safety.contractor;

/**
 * @param approved       合格单位
 * @param pending        待审核
 * @param blacklisted    黑名单
 * @param expiring       合格单位里资质、保险 30 天内到期或已过期的
 * @param workerWarnings 在场人员里入厂培训已过期或 30 天内到期的
 */
public record ContractorOverviewVO(long approved, long pending, long blacklisted, long expiring, long workerWarnings) {
}
