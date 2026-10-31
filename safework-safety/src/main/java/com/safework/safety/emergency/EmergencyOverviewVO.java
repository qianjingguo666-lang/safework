package com.safework.safety.emergency;

/**
 * 应急页面顶部统计
 *
 * @param responding    正在响应的事件数（大于 0 时页面顶部红色提示）
 * @param drillWarnings 演练逾期或从没演练过的启用预案数
 */
public record EmergencyOverviewVO(long responding, long reportedThisYear, long plans, long drillWarnings) {
}
