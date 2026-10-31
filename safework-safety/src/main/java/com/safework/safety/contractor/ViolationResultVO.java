package com.safework.safety.contractor;

/**
 * @param score       记录后的安全评分
 * @param blacklisted 这次违章导致自动列入黑名单
 */
public record ViolationResultVO(int score, boolean blacklisted) {
}
