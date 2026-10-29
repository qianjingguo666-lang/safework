import { getToken } from '@/utils/auth'

/**
 * 读取后端的 SSE 流。没用浏览器自带的 EventSource：它不能带 Authorization 请求头，也不支持 POST。
 * 后端格式：event:delta\ndata:{"t":"..."}\n\n
 * handlers: { 事件名(data) }，例如 { sources, delta, done, error } 或 { unread, message }
 */
export async function openStream({ url, method = 'GET', body, handlers, signal }) {
  const res = await fetch(`/api${url}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      // 同时接受 JSON：限流、未登录等错误会在流开始前以普通 JSON 返回
      Accept: 'text/event-stream, application/json',
      Authorization: `Bearer ${getToken() || ''}`
    },
    body: body ? JSON.stringify(body) : undefined,
    signal
  })
  const type = res.headers.get('content-type') || ''
  if (!res.ok || type.includes('application/json')) {
    let msg = `请求失败（HTTP ${res.status}）`
    let code = res.status
    try {
      const j = await res.json()
      msg = j.msg || msg
      code = j.code || code
      if (j.code === 401) msg = '登录已过期，请重新登录'
    } catch { /* 不是 JSON */ }
    const err = new Error(msg)
    err.code = code
    throw err
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 一个事件以空行结束；最后一段可能不完整，留到下次
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop()
    for (const block of blocks) dispatch(block, handlers)
  }
  if (buffer.trim()) dispatch(buffer, handlers)
}

/** AI 问答用：POST 请求的流 */
export function postStream(url, body, handlers, signal) {
  return openStream({ url, method: 'POST', body, handlers, signal })
}

function dispatch(block, handlers) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r?\n/)) {
    // 以冒号开头的是注释（后端的心跳 :ping），忽略
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length) return
  let payload = data.join('\n')
  try { payload = JSON.parse(payload) } catch { /* 保持字符串 */ }
  handlers[event]?.(payload)
}
