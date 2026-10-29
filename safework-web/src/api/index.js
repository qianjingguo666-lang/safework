import request from './request'

// ---------- 认证 ----------
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
