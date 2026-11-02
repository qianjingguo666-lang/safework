<template>
  <div>
    <div class="m-tabs">
      <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="switchTab(t.key)">{{ t.label }}</button>
    </div>
    <router-link v-for="t in list" :key="t.id" :to="`/m/ticket/${t.id}`" class="m-item">
      <div class="m-item-head">
        <span class="m-item-title">{{ t.title }}</span>
        <el-tag size="small" :type="TICKET_STATUS[t.status]?.type">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ t.ticketNo }} · {{ TICKET_TYPES[t.ticketType]?.label }}</div>
      <div class="m-muted">{{ t.location }}</div>
      <div class="m-muted">计划 {{ shortTime(t.planStartTime) }} 开始 · 监护人 {{ t.guardianName }}</div>
      <div v-if="t.status === 'PENDING' && t.currentStep" class="step">当前：{{ t.currentStep }}</div>
    </router-link>
    <div v-if="!loading && !list.length" class="m-empty">{{ tab === 'todo' ? '没有等你审批的作业票' : '没有作业票' }}</div>
    <button v-if="list.length < total" class="m-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageTickets } from '@/api'
import { useUserStore } from '@/stores/user'
import { TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'
import { shortTime } from '@/utils/mobile'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const tabs = computed(() => [
  store.hasPerm('ticket:approve') && { key: 'todo', label: '待我审批', params: { todo: true } },
  { key: 'mine', label: '我的作业', params: { mine: true } },
  { key: 'working', label: '作业中', params: { status: 'IN_PROGRESS' } }
].filter(Boolean))
const tab = ref(tabs.value.some((t) => t.key === route.query.tab) ? route.query.tab : tabs.value[0].key)
const list = ref([])
const total = ref(0)
const loading = ref(false)
let current = 1

async function load(more = false) {
  loading.value = true
  current = more ? current + 1 : 1
  try {
    const params = tabs.value.find((t) => t.key === tab.value).params
    const page = await pageTickets({ ...params, current, size: 10 })
    list.value = more ? [...list.value, ...page.records] : page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function switchTab(k) {
  tab.value = k
  router.replace({ query: { tab: k } })
}
onMounted(() => load())
</script>

<style scoped>
.step { margin-top: 4px; font-size: 13px; color: #e6a23c; }
</style>
