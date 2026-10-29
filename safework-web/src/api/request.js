import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/**
 * 统一请求封装：
 * - 自动带上 token
 * - 后端统一返回 {code, msg, data}，code 不是 200 时弹出错误并 reject
 * - 401 时清除登录信息，跳回登录页
 */
const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (resp) => {
    const { code, msg, data } = resp.data
    if (code === 200) return data
    if (code === 401) {
      useUserStore().reset()
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg))
  },
  (err) => {
    ElMessage.error(err.code === 'ECONNABORTED' ? '请求超时' : '无法连接服务器，请确认后端已启动')
    return Promise.reject(err)
  }
)

export default request
