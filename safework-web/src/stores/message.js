import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { h } from 'vue'
import router from '@/router'
import { latestMessages, readMessage, unreadMessages } from '@/api'
import { openStream } from '@/utils/sse'

export const CATEGORY = {
  TODO: { label: '待办', type: 'primary' },
  NOTICE: { label: '通知', type: 'success' },
  WARN: { label: '预警', type: 'danger' },
  ANNOUNCE: { label: '公告', type: 'warning' }
}

/**
 * 站内消息：页面打开期间保持一个 SSE 长连接，收到新消息时更新小红点并弹出提醒。
 * 断线自动重连（3 秒、6 秒……最长 60 秒），token 失效（401）时停止。
 */
export const useMessageStore = defineStore('message', {
  state: () => ({
    unread: 0,
    latest: [],
    connected: false,
    controller: null,
    retryDelay: 3000,
    retryTimer: null
  }),
  actions: {
    async refresh() {
      try {
        const [stats, latest] = await Promise.all([unreadMessages(), latestMessages()])
        this.unread = Number(stats.total)
        this.latest = latest
      } catch { /* 请求层已经提示过 */ }
    },
    connect() {
      if (this.controller) return
      this.controller = new AbortController()
      const signal = this.controller.signal
      this.refresh()
      openStream({
        url: '/message/stream',
        signal,
        handlers: {
          unread: (d) => { this.unread = Number(d.count); this.connected = true; this.retryDelay = 3000 },
          message: (m) => this.onMessage(m)
        }
      }).then(() => this.reconnect(signal), (err) => {
        this.connected = false
        if (signal.aborted || err.code === 401) return
        this.reconnect(signal)
      })
    },
    /** 服务器 30 分钟主动结束连接、网络断开、后端重启：等一会儿重连 */
    reconnect(signal) {
      this.connected = false
      this.controller = null
      if (signal.aborted) return
      this.retryTimer = setTimeout(() => this.connect(), this.retryDelay)
      this.retryDelay = Math.min(this.retryDelay * 2, 60000)
    },
    disconnect() {
      clearTimeout(this.retryTimer)
      this.controller?.abort()
      this.controller = null
      this.connected = false
    },
    onMessage(m) {
      this.unread += 1
      this.latest = [m, ...this.latest.filter((x) => x.id !== m.id)].slice(0, 5)
      const n = ElNotification({
        title: `${CATEGORY[m.category]?.label || '消息'}：${m.title}`,
        message: h('div', { class: 'msg-pop' }, [h('div', m.content || ''), m.senderName ? h('div', { style: 'color:#999;margin-top:4px' }, `来自 ${m.senderName}`) : null]),
        type: m.category === 'WARN' ? 'error' : m.category === 'TODO' ? 'warning' : 'info',
        duration: m.category === 'WARN' ? 0 : 6000,
        position: 'bottom-right',
        onClick: () => {
          n.close()
          this.open(m)
        }
      })
    },
    /** 点击消息：标记已读，跳转到对应单据；公告跳到消息中心查看全文 */
    async open(m) {
      if (!m.readFlag) {
        try {
          await readMessage(m.id)
          m.readFlag = 1
          this.latest = this.latest.filter((x) => x.id !== m.id)
        } catch { /* ignore */ }
      }
      if (m.link) router.push(m.link)
      else if (m.bizType === 'NOTICE') router.push(`/message?notice=${m.bizId}`)
      else router.push('/message')
    }
  }
})
