const TOKEN_KEY = 'safework_token'
const TENANT_KEY = 'safework_tenant'

// localStorage 在隐私模式等情况下可能不可用，统一 try/catch
function safeGet(key) {
  try { return localStorage.getItem(key) } catch { return null }
}
function safeSet(key, value) {
  try { localStorage.setItem(key, value) } catch { /* ignore */ }
}
function safeRemove(key) {
  try { localStorage.removeItem(key) } catch { /* ignore */ }
}

export const getToken = () => safeGet(TOKEN_KEY)
export const setToken = (t) => safeSet(TOKEN_KEY, t)
export const getTenantName = () => safeGet(TENANT_KEY) || ''
export const setTenantName = (n) => safeSet(TENANT_KEY, n)
export function clearAuth() {
  safeRemove(TOKEN_KEY)
  safeRemove(TENANT_KEY)
}
