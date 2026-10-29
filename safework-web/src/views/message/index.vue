<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.readFlag" @change="search">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button :value="0">未读 {{ stats.total ? `(${stats.total})` : '' }}</el-radio-button>
        <el-radio-button :value="1">已读</el-radio-button>
      </el-radio-group>
      <el-select v-model="query.category" placeholder="分类" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in CATEGORY" :key="k" :label="`${v.label}${stats[k] ? ` (${stats[k]})` : ''}`" :value="k" />
      </el-select>
      <div class="spacer" />
      <el-button :disabled="!stats.total" @click="readAll">全部标为已读</el-button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!list.length && !loading" description="没有消息" />
      <div v-for="m in list" :key="m.id" class="item" :class="{ unread: !m.readFlag }" @click="open(m)">
        <span class="dot" />
        <div class="body">
          <div class="title">
            <el-tag size="small" :type="CATEGORY[m.category]?.type" effect="plain">{{ CATEGORY[m.category]?.label }}</el-tag>
            <span>{{ m.title }}</span>
          </div>
          <div class="content">{{ m.content }}</div>
          <div class="meta">{{ m.createTime }} · {{ m.senderName || '系统' }}</div>
        </div>
        <el-icon v-if="m.link || m.bizType === 'NOTICE'" class="arrow"><ArrowRight /></el-icon>
      </div>
    </div>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="noticeVisible" :title="notice?.title" width="600px">
      <div v-if="notice" class="notice">
        <div class="notice-meta">
          <el-tag v-if="notice.level === 'IMPORTANT'" type="danger" size="small">重要</el-tag>
          {{ notice.publisherName }} 发布于 {{ notice.createTime }}
        </div>
        <div class="notice-content">{{ notice.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getNotice, pageMessages, readAllMessages, readMessage, unreadMessages } from '@/api'
import { CATEGORY, useMessageStore } from '@/stores/message'

const route = useRoute()
const router = useRouter()
const msgStore = useMessageStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const stats = ref({})
const query = reactive({ current: 1, size: 10, readFlag: '', category: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const [page, s] = await Promise.all([pageMessages(params), unreadMessages()])
    list.value = page.records
    total.value = page.total
    stats.value = Object.fromEntries(Object.entries(s).map(([k, v]) => [k, Number(v)]))
    msgStore.unread = stats.value.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}

async function readAll() {
  await readAllMessages(query.category || undefined)
  msgStore.refresh()
  load()
}

async function open(m) {
  if (!m.readFlag) {
    await readMessage(m.id)
    m.readFlag = 1
    stats.value.total = Math.max(0, stats.value.total - 1)
    msgStore.refresh()
  }
  if (m.bizType === 'NOTICE') showNotice(m.bizId)
  else if (m.link) router.push(m.link)
}

const noticeVisible = ref(false)
const notice = ref(null)
async function showNotice(id) {
  notice.value = await getNotice(id)
  noticeVisible.value = true
}

// 从小铃铛点公告进来：/message?notice=ID
watch(() => route.query.notice, (id) => { if (id) showNotice(id) }, { immediate: true })
onMounted(load)
</script>

<style scoped>
.list { min-height: 200px; }
.item { display: flex; align-items: flex-start; gap: 12px; padding: 14px 8px; border-bottom: 1px solid #f0f2f5; cursor: pointer; }
.item:hover { background: #f8fafc; }
.dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 7px; flex-shrink: 0; background: transparent; }
.unread .dot { background: #f56c6c; }
.body { flex: 1; min-width: 0; }
.title { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 8px; color: #8a919f; }
.unread .title { color: #1f2329; font-weight: 600; }
.content { color: #666; font-size: 13px; margin-top: 6px; line-height: 1.6; }
.meta { color: #aaa; font-size: 12px; margin-top: 6px; }
.arrow { color: #c0c4cc; margin-top: 4px; }
.notice-meta { color: #999; font-size: 13px; margin-bottom: 12px; display: flex; gap: 8px; align-items: center; }
.notice-content { white-space: pre-wrap; line-height: 1.8; color: #333; }
</style>
