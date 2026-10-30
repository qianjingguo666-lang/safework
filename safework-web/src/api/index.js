import request from './request'

// ---------- 认证 ----------
export const getCaptcha = () => request.get('/auth/captcha')
export const login = (data) => request.post('/auth/login', data)
export const getMe = () => request.get('/auth/me')
export const logout = () => request.post('/auth/logout')

// ---------- 用户 ----------
export const pageUsers = (params) => request.get('/system/user/page', { params })
export const createUser = (data) => request.post('/system/user', data)
export const getUserRoles = (id) => request.get(`/system/user/${id}/roles`)
export const assignUserRoles = (id, ids) => request.put(`/system/user/${id}/roles`, { ids })

// ---------- 角色 ----------
export const listRoles = () => request.get('/system/role/list')
export const createRole = (data) => request.post('/system/role', data)
export const getRoleMenus = (id) => request.get(`/system/role/${id}/menus`)
export const assignRoleMenus = (id, ids) => request.put(`/system/role/${id}/menus`, { ids })

// ---------- 菜单 ----------
export const menuTree = () => request.get('/system/menu/tree')

// ---------- 作业票 ----------
export const pageTickets = (params) => request.get('/ticket/page', { params })
export const getTicket = (id) => request.get(`/ticket/${id}`)
export const listGuardians = () => request.get('/ticket/guardians')
export const createTicket = (data) => request.post('/ticket', data)
export const updateTicket = (id, data) => request.put(`/ticket/${id}`, data)
/** action: submit / cancel / approve / reject / start / complete */
export const ticketAction = (id, action, data = {}) => request.post(`/ticket/${id}/${action}`, data)
// 作业票审批流程
export const listTicketFlows = () => request.get('/ticket/flow')
export const ticketFlowCandidates = () => request.get('/ticket/flow/candidates')
export const saveTicketFlow = (type, nodes) => request.put(`/ticket/flow/${type}`, nodes)
export const resetTicketFlow = (type) => request.delete(`/ticket/flow/${type}`)

// ---------- 用户（补充） ----------
export const userOptions = () => request.get('/system/user/options')
export const updateUser = (id, data) => request.put(`/system/user/${id}`, data)
export const changeUserStatus = (id, status) => request.put(`/system/user/${id}/status`, { status })
export const resetUserPassword = (id, password) => request.put(`/system/user/${id}/password`, { password })
export const changePassword = (data) => request.post('/auth/password', data)
export const unlockUser = (id) => request.put(`/system/user/${id}/unlock`)

// ---------- 登录日志、在线用户 ----------
export const pageLoginLogs = (params) => request.get('/system/loginlog/page', { params })
export const listOnlineUsers = (params) => request.get('/system/online/list', { params })
export const kickoutOnline = (sessionId) => request.delete(`/system/online/${sessionId}`)

// ---------- 平台端：企业、套餐 ----------
export const platformStats = () => request.get('/platform/tenant/stats')
export const pageTenants = (params) => request.get('/platform/tenant/page', { params })
export const createTenant = (data) => request.post('/platform/tenant', data)
export const updateTenant = (id, data) => request.put(`/platform/tenant/${id}`, data)
export const changeTenantStatus = (id, status) => request.put(`/platform/tenant/${id}/status`, { status })
export const resetTenantAdminPassword = (id, password) => request.put(`/platform/tenant/${id}/admin-password`, { password })
export const listPackages = () => request.get('/platform/package/list')
export const packageOptions = () => request.get('/platform/package/options')
export const packageMenuTree = () => request.get('/platform/package/menu-tree')
export const createPackage = (data) => request.post('/platform/package', data)
export const updatePackage = (id, data) => request.put(`/platform/package/${id}`, data)
export const changePackageStatus = (id, status) => request.put(`/platform/package/${id}/status`, { status })
export const removePackage = (id) => request.delete(`/platform/package/${id}`)
export const pagePlatformLoginLogs = (params) => request.get('/platform/loginlog/page', { params })

// ---------- 部门 ----------
export const deptTree = () => request.get('/system/dept/tree')
export const createDept = (data) => request.post('/system/dept', data)
export const updateDept = (id, data) => request.put(`/system/dept/${id}`, data)
export const removeDept = (id) => request.delete(`/system/dept/${id}`)

// ---------- 操作日志 ----------
export const pageOperLogs = (params) => request.get('/system/log/page', { params })

// ---------- 受限空间 ----------
export const pageSpaces = (params) => request.get('/safety/space/page', { params })
export const spaceOptions = () => request.get('/safety/space/options')
export const createSpace = (data) => request.post('/safety/space', data)
export const updateSpace = (id, data) => request.put(`/safety/space/${id}`, data)
export const changeSpaceStatus = (id, status) => request.put(`/safety/space/${id}/status`, { status })
export const getSpaceQrToken = (id) => request.get(`/safety/space/${id}/qr-token`)
export const resetSpaceQrToken = (id) => request.post(`/safety/space/${id}/qr-token/reset`)
export const scanSpace = (token) => request.get(`/public/space/${token}`)
// 作业票打印（A4 票面 + 签名 + 验真二维码）、扫纸质票上的二维码验真（不用登录）
export const printTicket = (id) => request.get(`/ticket/${id}/print`)
export const verifyTicket = (token) => request.get(`/public/ticket/${token}`)

// ---------- 隐患 ----------
export const pageHazards = (params) => request.get('/safety/hazard/page', { params })
export const getHazard = (id) => request.get(`/safety/hazard/${id}`)
export const reportHazard = (data) => request.post('/safety/hazard', data)
// 语音智能上报：录音（16k PCM）转文字 → 提取隐患信息填表
export const transcribeHazard = (pcm) => {
  const form = new FormData()
  form.append('audio', pcm, 'voice.pcm')
  return request.post('/ai/hazard/transcribe', form, { timeout: 60000 })
}
export const parseHazardText = (text) => request.post('/ai/hazard/parse', { text }, { timeout: 45000 })
/** action: assign / revoke / rectify / verify-pass / verify-fail */
export const hazardAction = (id, action, data = {}) => request.post(`/safety/hazard/${id}/${action}`, data)

// ---------- AI ----------
export const aiStatus = () => request.get('/ai/status')
export const pageKnowledge = (params) => request.get('/ai/knowledge/page', { params })
export const knowledgeChunks = (id) => request.get(`/ai/knowledge/${id}/chunks`, { params: { limit: 100 } })
export const uploadKnowledge = (file, title) => {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  // 上传后要解析、切分、生成向量，比普通接口慢，单独放宽超时
  return request.post('/ai/knowledge/upload', form, { timeout: 180000 })
}
export const embedKnowledge = (id) => request.post(`/ai/knowledge/${id}/embed`, null, { timeout: 180000 })
export const removeKnowledge = (id) => request.delete(`/ai/knowledge/${id}`)
export const pageChatLogs = (params) => request.get('/ai/chat/logs', { params })
export const agentTools = () => request.get('/ai/agent/tools')
export const confirmAgentAction = (id) => request.post(`/ai/agent/actions/${id}/confirm`, null, { timeout: 30000 })
export const cancelAgentAction = (id) => request.post(`/ai/agent/actions/${id}/cancel`)
export const reviewTicket = (id) => request.post(`/ai/ticket/${id}/review`, null, { timeout: 90000 })

// ---------- 消息、公告、定时任务 ----------
export const pageMessages = (params) => request.get('/message/page', { params })
export const unreadMessages = () => request.get('/message/unread')
export const latestMessages = () => request.get('/message/latest')
export const readMessage = (id) => request.put(`/message/${id}/read`)
export const readAllMessages = (category) => request.put('/message/read-all', null, { params: category ? { category } : {} })
export const getNotice = (id) => request.get(`/message/notice/${id}`)
export const pageNotices = (params) => request.get('/system/notice/page', { params })
export const publishNotice = (data) => request.post('/system/notice', data)
export const withdrawNotice = (id) => request.delete(`/system/notice/${id}`)
export const listJobs = () => request.get('/platform/job/list')
export const pageJobLogs = (params) => request.get('/platform/job/log/page', { params })
export const runJob = (code) => request.post(`/platform/job/${code}/run`, null, { timeout: 120000 })

// ---------- 附件、导入导出、看板 ----------
export const listFiles = (params) => request.get('/file/list', { params })
export const uploadFile = (bizType, bizId, tag, file) => {
  const form = new FormData()
  form.append('bizType', bizType)
  form.append('bizId', bizId)
  form.append('tag', tag)
  form.append('file', file)
  return request.post('/file/upload', form, { timeout: 120000 })
}
export const removeFile = (id) => request.delete(`/file/${id}`)
export const importSpaces = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/safety/space/import', form, { timeout: 120000 })
}
export const dashboardOverview = (days) => request.get('/dashboard/overview', { params: { days } })

// ---------- 安全培训 ----------
export const pageQuestions = (params) => request.get('/training/question/page', { params })
export const questionCategories = () => request.get('/training/question/categories')
export const createQuestion = (data) => request.post('/training/question', data)
export const updateQuestion = (id, data) => request.put(`/training/question/${id}`, data)
export const approveQuestions = (ids) => request.post('/training/question/approve', { ids })
export const removeQuestions = (ids) => request.post('/training/question/remove', { ids })
export const changeQuestionStatus = (id, status) => request.put(`/training/question/${id}/status`, null, { params: { status } })
// 大模型一次出十几道题需要几十秒
export const aiGenerateQuestions = (data) => request.post('/ai/question/generate', data, { timeout: 150000 })

export const pageExams = (params) => request.get('/training/exam/page', { params })
export const getExam = (id) => request.get(`/training/exam/${id}`)
export const examQuestionCount = (category) => request.get('/training/exam/question-count', { params: { category } })
export const createExam = (data) => request.post('/training/exam', data)
export const updateExam = (id, data) => request.put(`/training/exam/${id}`, data)
export const publishExam = (id) => request.post(`/training/exam/${id}/publish`)
export const closeExam = (id) => request.post(`/training/exam/${id}/close`)
export const removeExam = (id) => request.delete(`/training/exam/${id}`)
export const examStats = (id) => request.get(`/training/exam/${id}/stats`)
export const examRecords = (id, params) => request.get(`/training/exam/${id}/records`, { params })
export const examRecordDetail = (recordId) => request.get(`/training/exam/record/${recordId}`)

export const myExams = () => request.get('/training/my/exams')
export const startExam = (examId) => request.post(`/training/my/exams/${examId}/start`)
export const examPaper = (recordId) => request.get(`/training/my/record/${recordId}/paper`)
export const saveExamAnswers = (recordId, answers) => request.put(`/training/my/record/${recordId}/answers`, { answers })
export const submitExam = (recordId, answers) => request.post(`/training/my/record/${recordId}/submit`, { answers })
export const examResult = (recordId) => request.get(`/training/my/record/${recordId}/result`)

// ---------- 风险分级管控 ----------
export const riskLecOptions = () => request.get('/safety/risk/lec-options')
export const pageRisks = (params) => request.get('/safety/risk/page', { params })
export const riskOverview = () => request.get('/safety/risk/overview')
export const riskDetail = (id) => request.get(`/safety/risk/${id}`)
export const createRisk = (data) => request.post('/safety/risk', data)
export const updateRisk = (id, data) => request.put(`/safety/risk/${id}`, data)
export const changeRiskStatus = (id, status) => request.put(`/safety/risk/${id}/status`, null, { params: { status } })
export const riskTasks = () => request.get('/safety/risk/check/tasks')
export const submitRiskCheck = (id, data) => request.post(`/safety/risk/${id}/check`, data)
export const pageRiskChecks = (params) => request.get('/safety/risk/check/page', { params })
