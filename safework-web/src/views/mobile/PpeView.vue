<template>
  <div v-loading="loading">
    <div v-if="need" class="m-card alert">
      <b>{{ need }} 项劳保用品需要处理</b>
      <div class="m-muted">未发放、已过期的请找安全员领取；快到期的提前准备更换</div>
    </div>
    <div v-for="r in rows" :key="r.itemId" class="m-card item">
      <div class="m-item-head">
        <span class="m-item-title">{{ r.itemName }}</span>
        <el-tag size="small" :type="PPE_STATES[r.state]?.type" :effect="r.state === 'OK' ? 'light' : 'dark'">{{ PPE_STATES[r.state]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ PPE_CATEGORIES[r.category] }} · 应配 {{ r.quantity }}{{ r.unit }} · {{ r.posts }}</div>
      <div v-if="r.issueDate" class="m-muted">
        {{ r.issueDate }} 领取，{{ r.expireDate }} 到期
        <b v-if="r.state !== 'OK'" :class="r.daysLeft < 0 ? 'bad' : 'warn'">（{{ r.daysLeft < 0 ? `已过期 ${-r.daysLeft} 天` : `还剩 ${r.daysLeft} 天` }}）</b>
      </div>
      <div v-else class="m-muted bad">还没有领取</div>
    </div>
    <div v-if="!loading && !rows.length" class="m-empty">你所在的岗位还没有配置劳保用品标准</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ppeMine } from '@/api'
import { PPE_CATEGORIES, PPE_STATES } from '@/utils/dict'

const loading = ref(false)
const rows = ref([])
const need = computed(() => rows.value.filter((r) => r.state !== 'OK').length)
onMounted(async () => {
  loading.value = true
  try {
    rows.value = await ppeMine()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.alert { background: #fff7e6; border: 1px solid #ffd591; }
.alert b { color: #d46b08; }
.bad { color: #f5222d; }
.warn { color: #fa8c16; }
</style>
