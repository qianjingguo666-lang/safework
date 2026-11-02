package com.safework.ticket.mobile;

/**
 * 手机端首页的待办数字。没有对应权限的项返回 null（前端不显示那个入口），0 表示有权限但没有待办。
 * <p>
 * 用 Integer 而不是 Long：全局把 Long 序列化成字符串（防止 ID 在 JS 里丢精度），计数要返回数字，前端才能直接相加、比较。
 *
 * @param approve        待我审批的作业票
 * @param working        我申请或监护的、已批准待开工 / 作业中的作业票
 * @param rectify        由我整改、还没提交的隐患
 * @param rectifyOverdue 其中已过整改期限的
 * @param assign         待指派的隐患（安全员）
 * @param verify         待验收的隐患（安全员）
 * @param riskDue        今天到期或已逾期的风险巡检
 * @param unread         未读消息
 */
public record MobileHomeVO(Integer approve, Integer working, Integer rectify, Integer rectifyOverdue,
                           Integer assign, Integer verify, Integer riskDue, Integer unread) {
}
