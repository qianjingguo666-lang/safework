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
  async (resp) => {
    // 文件下载（导出 Excel、下载附件）：成功时直接返回响应；失败时后端返回的是 JSON，转成普通错误
    if (resp.config.responseType === 'blob') {
      if (!(resp.headers['content-type'] || '').includes('application/json')) return resp
      resp.data = JSON.parse(await resp.data.text())
    }
    const { code, msg, data } = resp.data
    if (code === 200) return data
    if (code === 401) {
      useUserStore().reset()
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    // 系统错误带上错误编号（traceId），用户截图反馈时开发者可以直接按编号查日志
    const traceId = resp.data.traceId
    ElMessage.error(code >= 500 && traceId ? `${msg || '请求失败'}（错误编号 ${traceId}）` : (msg || '请求失败'))
    const error = new Error(msg)
    error.code = code
    // 标记为“已提示过”，全局的未处理异常监听会忽略它，避免控制台重复报错
    error.handled = true
    return Promise.reject(error)
  },
  (err) => {
    ElMessage.error(err.code === 'ECONNABORTED' ? '请求超时' : '无法连接服务器，请确认后端已启动')
    err.handled = true
    return Promise.reject(err)
  }
)

export default request
