<template>
  <div>
    <div class="m-tabs">
      <button :class="{ on: unreadOnly }" @click="switchTab(true)">未读</button>
      <button :class="{ on: !unreadOnly }" @click="switchTab(false)">全部</button>
      <div class="spacer" />
      <button v-if="unreadOnly && list.length" class="readall" @click="readAll">全部已读</button>
    </div>
    <div v-for="m in list" :key="m.id" class="m-item" :class="{ unread: !m.readFlag }" @click="openMsg(m)">
      <div class="m-item-head">
        <el-tag size="small" :type="CAT[m.category]?.type">{{ CAT[m.category]?.label || m.category }}</el-tag>
        <span class="m-item-title">{{ m.title }}</span>
        <i v-if="!m.readFlag" class="dot" />
      </div>
      <div class="content">{{ m.content }}</div>
      <div class="m-muted">{{ m.senderName ? m.senderName + ' · ' : '' }}{{ shortTime(m.createTime) }}</div>
    </div>
    <div v-if="!loading && !list.length" class="m-empty">{{ unreadOnly ? '没有未读消息' : '没有消息' }}</div>
    <button v-if="list.length < total" class="m-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNotice, pageMessages, readAllMessages, readMessage } from '@/api'
import { mobileLink, shortTime } from '@/utils/mobile'

const emit = defineEmits(['unread'])
const router = useRouter()
const CAT = {
  TODO: { label: '待办', type: 'danger' },
  WARN: { label: '预警', type: 'warning' },
  NOTICE: { label: '通知', type: 'primary' },
  ANNOUNCE: { label: '公告', type: 'success' }
}
const unreadOnly = ref(true)
const list = ref([])
const total = ref(0)
const loading = ref(false)
let current = 1

async function load(more = false) {
  loading.value = true
  current = more ? current + 1 : 1
  try {
    const params = { current, size: 15 }
    if (unreadOnly.value) params.readFlag = 0
    const page = await pageMessages(params)
    list.value = more ? [...list.value, ...page.records] : page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function switchTab(v) {
  unreadOnly.value = v
  load()
}
async function openMsg(m) {
  if (!m.readFlag) {
    await readMessage(m.id)
    m.readFlag = 1
    emit('unread')
  }
  if (m.bizType === 'NOTICE') {
    const n = await getNotice(m.bizId)
    return ElMessageBox.alert(n.content, n.title, { confirmButtonText: '知道了', customClass: 'm-notice' }).catch(() => {})
  }
  const to = mobileLink(m.link)
  if (to) router.push(to)
  else if (m.link) ElMessage.info('这条消息的详情请到电脑端查看')
}
async function readAll() {
  await readAllMessages()
  emit('unread')
  load()
}
onMounted(() => load())
</script>

<style scoped>
.m-tabs { align-items: center; }
.spacer { flex: 1; }
.m-tabs .readall { background: none; color: #1f6feb; padding: 7px 4px; }
.m-item { cursor: pointer; }
.m-item.unread .m-item-title { color: #1f2329; }
.m-item:not(.unread) { opacity: .75; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #f5222d; flex: none; }
.content { font-size: 14px; color: #555; margin: 2px 0 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
</style>

<style>
.m-notice { width: 92vw; max-width: 480px; }
.m-notice .el-message-box__message { white-space: pre-wrap; max-height: 60vh; overflow-y: auto; }
</style>
