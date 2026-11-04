<template>
  <div>
    <div class="m-tabs">
      <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="switchTab(t.key)">{{ t.label }}</button>
    </div>
    <router-link v-for="k in list" :key="k.id" :to="`/m/check/${k.id}`" class="m-item">
      <div class="m-item-head">
        <span class="m-item-title">{{ k.planName }}</span>
        <el-tag size="small" :type="CHECK_TASK_STATES[k.state]?.type" :effect="k.state === 'OVERDUE' ? 'dark' : 'light'">{{ CHECK_TASK_STATES[k.state]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ k.location }}</div>
      <div class="m-muted">
        {{ CHECK_TYPES[k.checkType] }} · {{ k.itemCount }} 项 ·
        <template v-if="k.status === 'DONE'">
          <span :class="k.failCount ? 'bad' : 'good'">{{ k.failCount ? `${k.failCount} 项不合格` : '全部合格' }}</span>
        </template>
        <template v-else>截止 {{ k.dueDate }}</template>
      </div>
    </router-link>
    <div v-if="!loading && !list.length" class="m-empty">{{ tab === 'todo' ? '没有待检查的任务' : '没有检查记录' }}</div>
    <button v-if="list.length < total" class="m-more" :disabled="loading" @click="load(true)">{{ loading ? '加载中…' : '加载更多' }}</button>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { checkTaskPage } from '@/api'
import { CHECK_TASK_STATES, CHECK_TYPES } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
// 手机上只看分给自己的（安全员在电脑上看全部）
const tabs = [
  { key: 'todo', label: '待检查', params: { mine: true, state: 'PENDING' } },
  { key: 'done', label: '已完成', params: { mine: true, state: 'DONE' } }
]
const tab = ref(tabs.some((t) => t.key === route.query.tab) ? route.query.tab : 'todo')
const list = ref([])
const total = ref(0)
const loading = ref(false)
let current = 1

async function load(more = false) {
  loading.value = true
  current = more ? current + 1 : 1
  try {
    const page = await checkTaskPage({ ...tabs.find((t) => t.key === tab.value).params, current, size: 10 })
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
.good { color: #67c23a; }
.bad { color: #f5222d; }
</style>
