export const INJURY_STATUS = {
  REPORTED: { label: '待申请认定', type: 'warning' },
  APPLIED: { label: '已申请认定', type: 'primary' },
  RECOGNIZED: { label: '认定为工伤', type: 'danger' },
  NOT_RECOGNIZED: { label: '不予认定', type: 'info' },
  APPRAISED: { label: '已鉴定', type: 'danger' },
  CLOSED: { label: '已结案', type: 'success' }
}
export const COST_CATEGORIES = {
  MEDICAL: '医疗费', FOOD: '住院伙食补助费', TRAVEL: '交通食宿费', REHAB: '康复费',
  NURSING: '护理费', LEAVE_WAGE: '停工留薪期工资', LUMP_SUM: '一次性伤残补助金', OTHER: '其他'
}
export const PAYERS = { FUND: '工伤保险基金', COMPANY: '单位' }
export const GRADES = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((g) => ({ value: g, label: g === 0 ? '未达到伤残等级' : `${'一二三四五六七八九十'[g - 1]}级伤残` }))

/** 申请认定倒计时标签 */
export function deadlineTag(d) {
  if (!d || d.state === 'DONE') return null
  if (d.state === 'OVERDUE') return { type: 'danger', text: `已超期 ${-d.daysLeft} 天` }
  if (d.state === 'SOON') return { type: 'warning', text: d.daysLeft === 0 ? '今天截止' : `还剩 ${d.daysLeft} 天` }
  return { type: 'info', text: `还剩 ${d.daysLeft} 天` }
}
export const money = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
export const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
