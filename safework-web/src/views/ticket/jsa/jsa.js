export const JSA_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  REVIEWING: { label: '审核中', type: 'warning' },
  PUBLISHED: { label: '已发布', type: 'success' },
  REJECTED: { label: '被驳回', type: 'danger' },
  ARCHIVED: { label: '已停用', type: 'info' }
}
/** 风险等级：L × S，1~4 低、5~9 一般、10~14 较大、15~25 重大 */
export const RISK_LEVELS = {
  LOW: { label: '低风险', color: '#409eff', type: 'primary' },
  GENERAL: { label: '一般风险', color: '#e6a23c', type: 'warning' },
  HIGH: { label: '较大风险', color: '#f08c2b', type: 'warning' },
  MAJOR: { label: '重大风险', color: '#f56c6c', type: 'danger' }
}
export function riskLevel(r) {
  if (r >= 15) return 'MAJOR'
  if (r >= 10) return 'HIGH'
  if (r >= 5) return 'GENERAL'
  return 'LOW'
}
export const L_HINT = '可能性：1 几乎不可能、2 不太可能、3 可能、4 很可能、5 经常发生'
export const S_HINT = '严重度：1 轻微、2 轻伤、3 重伤、4 1 人死亡、5 多人死亡'
