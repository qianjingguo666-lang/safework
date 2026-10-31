package com.safework.safety.cert;

/**
 * @param total    证书总数
 * @param holders  持证人数
 * @param expiring 30 天内到期（含复审）
 * @param expired  已过期或复审逾期
 */
public record CertOverviewVO(long total, long holders, long expiring, long expired) {
}
