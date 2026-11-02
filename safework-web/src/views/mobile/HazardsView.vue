<template>
  <div>
    <div class="m-tabs">
      <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="switchTab(t.key)">{{ t.label }}</button>
    </div>
    <router-link v-for="h in list" :key="h.id" :to="`/m/hazard/${h.id}`" class="m-item">
      <div class="m-item-head">
        <el-tag size="small" effect="dark" :type="HAZARD_LEVELS[h.level]?.type">{{ h.level === 'MAJOR' ? '重大' : '一般' }}</el-tag>
        <span class="m-item-title">{{ h.title }}</span>
        <el-tag size="small" :type="HAZARD_STATUS[h.status]?.type">{{ HAZARD_STATUS[h.status]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ h.hazardNo }} · {{ h.location }}</div>
      <div class="m-muted">
        上报 {{ h.reporterName }} {{ shortTime(h.createTime) }}
        <template v-if="h.rectifierName"> · 整改 {{ h.rectifierName }}</template>
      </div>
      <div v-if="h.deadline && h.status === 'RECTIFYING'" class="deadline" :class="{ overdue: h.overdue }">
        {{ h.overdue ? '已逾期' : '期限' }} {{ shortTime(h.deadline) }}
      </div>
    </router-link>
    <div v-if="!loading && !list.length" class="m-empty">没有隐患</div>
    <button v-if="list.length < total" class="m-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button>

    <button v-if="store.hasPerm('safety:hazard:report')" class="fab" @click="router.push('/m/report')"><el-icon><Camera /></el-icon></button>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageHazards } from '@/api'
import { useUserStore } from '@/stores/user'
import { HAZARD_LEVELS, HAZARD_STATUS } from '@/utils/dict'
import { shortTime } from '@/utils/mobile'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const handler = store.hasPerm('safety:hazard:handle')
const tabs = computed(() => [
  { key: 'rectify', label: '待我整改', params: { mine: true, status: 'RECTIFYING' } },
  handler && { key: 'assign', label: '待指派', params: { status: 'REPORTED' } },
  handler && { key: 'verify', label: '待验收', params: { status: 'PENDING_VERIFY' } },
  { key: 'all', label: handler ? '全部' : '与我有关', params: {} }
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
    const page = await pageHazards({ ...params, current, size: 10 })
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
.deadline { margin-top: 4px; font-size: 13px; color: #e6a23c; }
.deadline.overdue { color: #f5222d; font-weight: 600; }
.fab { position: fixed; right: 18px; bottom: calc(76px + env(safe-area-inset-bottom)); width: 54px; height: 54px; border: 0; border-radius: 50%; color: #fff; font-size: 26px; background: linear-gradient(135deg, #ff7a45, #f5222d); box-shadow: 0 4px 12px rgba(245, 34, 45, .35); display: flex; align-items: center; justify-content: center; }
</style>
