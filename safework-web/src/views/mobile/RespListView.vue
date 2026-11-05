<template>
  <div v-loading="loading">
    <div v-for="r in rows" :key="r.book.id" class="m-card" @click="open(r)">
      <div class="m-item-head">
        <span class="m-item-title">{{ r.book.title }}</span>
        <el-tag v-if="r.signed" type="success" size="small">已签署</el-tag>
        <el-tag v-else-if="r.book.status === 'PUBLISHED'" type="warning" effect="dark" size="small">待签署</el-tag>
        <el-tag v-else type="info" size="small">已结束</el-tag>
      </div>
      <div class="m-muted">签署截止 {{ r.book.deadline }}<template v-if="r.late"> · 逾期签署</template><template v-if="r.assessed"> · 已考核</template></div>
    </div>
    <div v-if="!loading && !rows.length" class="m-card m-muted">还没有发给你的安全生产责任书</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { respMine, respMySignId } from '@/api'

const router = useRouter()
const loading = ref(false)
const rows = ref([])
async function open(r) {
  router.push(`/m/resp/${await respMySignId(r.book.id)}`)
}
onMounted(async () => {
  loading.value = true
  try {
    rows.value = await respMine()
  } finally {
    loading.value = false
  }
})
</script>
