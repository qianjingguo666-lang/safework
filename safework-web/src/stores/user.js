import { defineStore } from 'pinia'
import { getMe, login as apiLogin, logout as apiLogout } from '@/api'
import { clearAuth, getTenantName, getToken, setTenantName, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    tenantName: getTenantName(),
    user: null,
    roles: [],
    permissions: [],
    menus: [],
    loaded: false
  }),
  getters: {
    nickname: (s) => s.user?.nickname || s.user?.username || ''
  },
  actions: {
    async login(form) {
      const data = await apiLogin(form)
      this.token = data.token
      this.tenantName = data.tenantName
      setToken(data.token)
      setTenantName(data.tenantName)
    },
    /** 获取用户信息、权限、菜单（刷新页面后会重新调用） */
    async fetchMe() {
      const data = await getMe()
      this.user = data.user
      this.roles = data.roles
      this.permissions = data.permissions
      this.menus = data.menus
      this.loaded = true
      return data
    },
    hasPerm(perm) {
      if (!perm) return true
      return this.permissions.includes('*') || this.permissions.includes(perm)
    },
    async logout() {
      try { await apiLogout() } catch { /* token 已失效也要继续退出 */ }
      this.reset()
    },
    reset() {
      clearAuth()
      this.$reset()
      this.token = null
      this.tenantName = ''
    }
  }
})
