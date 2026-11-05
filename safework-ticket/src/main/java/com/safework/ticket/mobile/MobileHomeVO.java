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
 * @param check          我待做的安全检查（含逾期）
 * @param checkOverdue   其中已逾期的
 * @param ppe            我的劳保用品里需要处理的（未发放、已过期、30 天内到期）
 * @param ppeExpired     其中未发放或已过期的
 * @param health         职业健康要处理的（体检从没做 / 逾期 / 30 天内到期算 1 项，加上未处理的异常体检数）；不接害为空
 * @param healthState    体检状态 NEVER / OVERDUE / DUE_SOON / OK
 * @param resp           待我签署的安全生产责任书；没有菜单也没有责任书为空
 * @param accident       我还没完成的事故整改措施；没有菜单也没有措施为空
 * @param chem           我负责的危化品单元里超储的种数；不是单元负责人为空
 * @param moc            要我处理的变更（评估、审批、实施、检查、恢复）；没有菜单为空
 * @param unread         未读消息
 */
public record MobileHomeVO(Integer approve, Integer working, Integer rectify, Integer rectifyOverdue,
                           Integer assign, Integer verify, Integer riskDue, Integer check, Integer checkOverdue,
                           Integer ppe, Integer ppeExpired,
                           Integer health, String healthState,
                           Integer resp, Integer accident, Integer chem, Integer moc,
                           Integer unread) {
}
