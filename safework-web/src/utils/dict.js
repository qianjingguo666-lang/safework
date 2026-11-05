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

// 人员资质
export const CERT_TYPES = {
  CONFINED_SPACE: '有限空间作业安全培训合格证',
  GUARDIAN: '特殊作业监护人培训合格证',
  WELDING: '特种作业操作证（焊接与热切割）',
  HEIGHT: '特种作业操作证（高处作业）',
  ELECTRICIAN: '特种作业操作证（电工）',
  HAZCHEM: '特种作业操作证（危险化学品）',
  SAFETY_MANAGER: '安全生产管理人员安全资格证',
  OTHER: '其他'
}
export const CERT_STATES = {
  VALID: { label: '有效', type: 'success' },
  EXPIRING: { label: '即将到期', type: 'warning' },
  EXPIRED: { label: '已过期', type: 'danger' }
}
export const CERT_HOLDER_ROLES = { APPLICANT: '作业负责人', GUARDIAN: '监护人', WORKER: '承包商作业人员' }

// 承包商
export const CONTRACTOR_STATUS = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '合格', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' },
  BLACKLISTED: { label: '黑名单', type: 'danger' }
}
export const CONTRACTOR_ACTIONS = {
  APPROVE: { label: '审核通过', type: 'success', comment: false },
  REJECT: { label: '驳回', type: 'warning', comment: true },
  RESUBMIT: { label: '重新提交审核', type: 'primary', comment: false },
  BLACKLIST: { label: '列入黑名单', type: 'danger', comment: true },
  LIFT: { label: '解除黑名单', type: 'primary', comment: true }
}

// 设备设施
export const EQUIP_CATEGORIES = { SPECIAL: '特种设备', ACCESSORY: '安全附件', DETECTOR: '检测报警仪器', FIRE: '消防设施', OTHER: '其他' }
export const EQUIP_STATUS = {
  IN_USE: { label: '在用', type: 'success' },
  STOPPED: { label: '停用', type: 'warning' },
  SCRAPPED: { label: '报废', type: 'info' }
}
export const EQUIP_STATES = {
  OVERDUE: { label: '检验逾期', type: 'danger' },
  DUE_SOON: { label: '即将到期', type: 'warning' },
  NEVER: { label: '从未检验', type: 'danger' },
  OK: { label: '正常', type: 'success' },
  NOT_IN_USE: { label: '-', type: 'info' }
}

// 安全检查
export const CHECK_TYPES = { DAILY: '日常检查', SPECIAL: '专项检查', HOLIDAY: '节假日检查', SEASONAL: '季节性检查', COMPREHENSIVE: '综合检查' }
export const CHECK_CYCLES = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月', ONCE: '一次性' }
export const CHECK_TASK_STATES = {
  PENDING: { label: '待检查', type: 'primary' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  DONE: { label: '已完成', type: 'success' }
}

// 劳保用品
export const PPE_CATEGORIES = { HEAD: '头部防护', RESPIRATORY: '呼吸防护', EYE_FACE: '眼面防护', HEARING: '听力防护', HAND: '手部防护', FOOT: '足部防护', BODY: '躯体防护', FALL: '坠落防护' }
export const PPE_STATES = {
  EXPIRED: { label: '已过期', type: 'danger' },
  MISSING: { label: '未发放', type: 'danger' },
  DUE_SOON: { label: '30 天内到期', type: 'warning' },
  OK: { label: '正常', type: 'success' }
}

// 职业健康
export const OH_CATEGORIES = { CHEMICAL: '化学因素', DUST: '粉尘', PHYSICAL: '物理因素', BIOLOGICAL: '生物因素', RADIATION: '放射性因素' }
export const OH_EXAM_TYPES = { PRE: '上岗前', ON: '在岗期间', LEAVE: '离岗时', EMERGENCY: '应急' }
export const OH_CONCLUSIONS = {
  NORMAL: { label: '目前未见异常', type: 'success' },
  OTHER: { label: '其他疾病或异常', type: 'info' },
  RECHECK: { label: '需要复查', type: 'warning' },
  SUSPECT: { label: '疑似职业病', type: 'danger' },
  CONTRAINDICATION: { label: '职业禁忌证', type: 'danger' }
}
export const OH_STATES = {
  OVERDUE: { label: '体检逾期', type: 'danger' },
  NEVER: { label: '从未体检', type: 'danger' },
  DUE_SOON: { label: '30 天内到期', type: 'warning' },
  OK: { label: '正常', type: 'success' }
}

// 安全生产责任制
export const RESP_BOOK_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '签署中', type: 'primary' },
  CLOSED: { label: '已结束', type: 'success' }
}
export const RESP_SIGN_STATUS = {
  PENDING: { label: '待签署', type: 'warning' },
  SIGNED: { label: '已签署', type: 'success' }
}
export const RESP_GRADES = { 优秀: 'success', 良好: 'primary', 合格: 'warning', 不合格: 'danger' }

// 事故调查
export const ACCIDENT_STATES = {
  INVESTIGATING: { label: '调查中', type: 'primary' },
  OVERDUE: { label: '调查超期', type: 'danger' },
  CLOSED: { label: '已结案', type: 'success' }
}
export const ACCIDENT_MEASURE_STATES = {
  OPEN: { label: '进行中', type: 'primary' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  DONE: { label: '已完成', type: 'success' }
}
export const ACCIDENT_DUTIES = { DIRECT: '直接责任', MAIN: '主要责任', LEADER: '领导责任', MANAGEMENT: '管理责任' }

// 危险化学品
export const CHEM_CATEGORIES = { EXPLOSIVE: '爆炸物', FLAMMABLE_GAS: '易燃气体', FLAMMABLE_LIQUID: '易燃液体', FLAMMABLE_SOLID: '易燃固体', OXIDIZER: '氧化性物质', TOXIC: '急性毒性', CORROSIVE: '腐蚀性物质', OTHER: '其他' }
export const CHEM_STORE_TYPES = { TANK_AREA: '罐区', WAREHOUSE: '库区（仓库）', PRODUCTION: '生产装置' }
export const CHEM_FLOW_TYPES = { IN: { label: '入库', type: 'success' }, OUT: { label: '出库', type: 'warning' }, CHECK: { label: '盘点', type: 'info' } }
export const CHEM_LEVELS = { 1: { label: '一级', color: '#cf1322' }, 2: { label: '二级', color: '#fa541c' }, 3: { label: '三级', color: '#faad14' }, 4: { label: '四级', color: '#1677ff' } }
/** 吨数显示：去掉多余的 0 */
export const ton = (v) => (v == null ? '-' : String(Number(v)))
