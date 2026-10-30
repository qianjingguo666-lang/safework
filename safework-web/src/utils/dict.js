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
