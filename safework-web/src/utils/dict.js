// 和后端枚举一一对应

export const TICKET_TYPES = {
  CONFINED_SPACE: { label: '受限空间作业', gas: true },
  HOT_WORK: { label: '动火作业', gas: true },
  HEIGHT: { label: '高处作业', gas: false }
}

export const TICKET_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已批准', type: 'primary' },
  IN_PROGRESS: { label: '作业中', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const TICKET_ACTIONS = {
  CREATE: '创建',
  EDIT: '修改',
  SUBMIT: '提交审批',
  APPROVE: '审批通过',
  REJECT: '驳回',
  START: '开始作业',
  COMPLETE: '作业完成',
  CANCEL: '取消'
}

export const RISK_LEVELS = {
  HIGH: { label: '高风险', type: 'danger' },
  MEDIUM: { label: '中风险', type: 'warning' },
  LOW: { label: '低风险', type: 'success' }
}

export const SPACE_TYPES = ['储罐', '反应釜', '污水池', '地下有限空间', '管道', '其他']

export const HAZARD_LEVELS = {
  GENERAL: { label: '一般隐患', type: 'warning' },
  MAJOR: { label: '重大隐患', type: 'danger' }
}

export const HAZARD_CATEGORIES = ['设备设施', '作业环境', '人员行为', '安全管理']

export const HAZARD_STATUS = {
  REPORTED: { label: '待指派', type: 'warning' },
  RECTIFYING: { label: '整改中', type: 'primary' },
  PENDING_VERIFY: { label: '待验收', type: 'warning' },
  CLOSED: { label: '已闭环', type: 'success' },
  REVOKED: { label: '已撤销', type: 'info' }
}

export const HAZARD_ACTIONS = {
  REPORT: '上报',
  ASSIGN: '指派整改',
  REVOKE: '撤销',
  RECTIFY: '提交整改',
  VERIFY_PASS: '验收通过',
  VERIFY_FAIL: '验收不通过'
}

export const ROLE_NAMES = { admin: '企业管理员', safety: '安全员', leader: '作业负责人', guardian: '监护人' }

/** 当前时间，格式 yyyy-MM-dd HH:mm:ss，offsetDays 可以往后推几天 */
export function nowStr(offsetDays = 0) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

// 安全培训
export const QUESTION_TYPES = {
  SINGLE: { label: '单选题', type: 'primary' },
  MULTI: { label: '多选题', type: 'warning' },
  JUDGE: { label: '判断题', type: 'success' }
}
export const QUESTION_STATUS = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '启用', type: 'success' },
  2: { label: '停用', type: 'info' }
}
export const QUESTION_CATEGORIES = ['受限空间作业', '动火作业', '高处作业', '应急救援', '通用安全']
export const EXAM_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '进行中', type: 'success' },
  CLOSED: { label: '已结束', type: 'info' }
}
/** 选项字母：0 → A */
export const letter = (i) => String.fromCharCode(65 + i)

// 风险分级管控（四色）
export const RISK_GRADES = {
  MAJOR: { label: '重大风险', short: '重大', color: '#e5484d', text: '#fff', control: '公司级' },
  HIGH: { label: '较大风险', short: '较大', color: '#f76b15', text: '#fff', control: '部门级' },
  GENERAL: { label: '一般风险', short: '一般', color: '#f5c400', text: '#3d3200', control: '班组级' },
  LOW: { label: '低风险', short: '低', color: '#3b82f6', text: '#fff', control: '岗位级' }
}
export const RISK_POINT_TYPES = ['设备设施', '作业活动', '区域场所']
export const CHECK_STATES = {
  OVERDUE: { label: '巡检逾期', type: 'danger' },
  TODAY: { label: '今天到期', type: 'warning' },
  UPCOMING: { label: '未到期', type: 'info' }
}

// ============ 应急管理 ============
export const ACCIDENT_TYPES = {
  FIRE_EXPLOSION: '火灾爆炸', POISONING: '中毒窒息', LEAK: '危化品泄漏', FALL: '高处坠落', ELECTRIC: '触电', MECHANICAL: '机械伤害', OTHER: '其他'
}
export const EMERGENCY_LEVELS = {
  GENERAL: { label: '一般事故', type: 'warning' },
  LARGER: { label: '较大事故', type: 'danger' },
  MAJOR: { label: '重大事故', type: 'danger' },
  EXTRA: { label: '特别重大事故', type: 'danger' }
}
export const EMERGENCY_STATUS = {
  REPORTED: { label: '已上报', type: 'warning' },
  RESPONDING: { label: '响应中', type: 'danger' },
  CLOSED: { label: '已结束', type: 'info' }
}
export const EMERGENCY_LOG_ACTIONS = { REPORT: '事故快报', START: '启动应急响应', UPDATE: '处置记录', CLOSE: '结束' }
export const PLAN_TYPES = { COMPREHENSIVE: '综合应急预案', SPECIAL: '专项应急预案', ONSITE: '现场处置方案' }
export const DRILL_STATES = {
  NEVER: { label: '从未演练', type: 'danger' },
  OVERDUE: { label: '演练逾期', type: 'danger' },
  DUE_SOON: { label: '即将到期', type: 'warning' },
  OK: { label: '正常', type: 'success' }
}
export const DRILL_STATUS = {
  PLANNED: { label: '计划中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}
export const DRILL_TYPES = { TABLETOP: '桌面推演', LIVE: '实战演练' }
