import { getToken } from '@/utils/auth'

/**
 * 读取后端的 SSE 流（POST 请求，所以不能用浏览器自带的 EventSource）。
 * 后端格式：event:delta\ndata:{"t":"..."}\n\n
 * handlers: { sources(list), delta(data), done(data), error(data) }
 */
export async function postStream(url, body, handlers, signal) {
  const res = await fetch(`/api${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      // 同时接受 JSON：限流、未登录等错误会在流开始前以普通 JSON 返回
      Accept: 'text/event-stream, application/json',
      Authorization: `Bearer ${getToken() || ''}`
    },
    body: JSON.stringify(body),
    signal
  })
  const type = res.headers.get('content-type') || ''
  if (!res.ok || type.includes('application/json')) {
    let msg = `请求失败（HTTP ${res.status}）`
    try {
      const j = await res.json()
      msg = j.msg || msg
      if (j.code === 401) msg = '登录已过期，请重新登录'
    } catch { /* 不是 JSON */ }
    throw new Error(msg)
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

function dispatch(block, handlers) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length) return
  let payload = data.join('\n')
  try { payload = JSON.parse(payload) } catch { /* 保持字符串 */ }
  handlers[event]?.(payload)
}
