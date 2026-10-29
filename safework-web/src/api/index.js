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

// ---------- 隐患 ----------
export const pageHazards = (params) => request.get('/safety/hazard/page', { params })
export const getHazard = (id) => request.get(`/safety/hazard/${id}`)
export const reportHazard = (data) => request.post('/safety/hazard', data)
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
export const reviewTicket = (id) => request.post(`/ai/ticket/${id}/review`, null, { timeout: 90000 })
