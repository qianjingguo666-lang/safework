<template>
  <div v-loading="loading">
    <div class="m-muted tip">你在参演名单里的应急演练。演练当天、开始前 2 小时起可以签到。</div>
    <div v-for="r in rows" :key="r.drill.id" class="m-item">
      <div class="m-item-head">
        <span class="m-item-title">{{ r.drill.title }}</span>
        <el-tag size="small" :type="DRILL_STATUS[r.drill.status]?.type">{{ DRILL_STATUS[r.drill.status]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ r.drill.planTime.slice(5, 16) }} · {{ r.drill.location || '地点待定' }} · {{ DRILL_TYPES[r.drill.drillType] }}</div>
      <div class="m-muted">我的角色：{{ r.role }}</div>
      <div v-if="r.drill.scenario" class="m-muted">情景：{{ r.drill.scenario }}</div>
      <div class="sign">
        <span v-if="r.signTime" class="ok">已签到 {{ r.signTime.slice(5, 16) }}</span>
        <el-button v-else-if="r.canSign" type="primary" size="large" class="btn" :loading="signing === r.drill.id" @click="sign(r)">签到</el-button>
        <span v-else-if="r.drill.status === 'PLANNED'" class="m-muted">还没到签到时间</span>
        <span v-else-if="r.attended === 0" class="bad">登记为缺席</span>
        <span v-else class="m-muted">未签到</span>
        <span v-if="r.drill.grade" class="grade">评估 {{ r.drill.totalScore }} 分（{{ r.drill.grade }}）</span>
      </div>
    </div>
    <div v-if="!loading && !rows.length" class="m-empty">最近没有你参加的应急演练</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { myDrills, signDrill } from '@/api'
import { DRILL_STATUS, DRILL_TYPES } from '@/utils/dict'

const rows = ref([])
const loading = ref(false)
const signing = ref(null)
async function load() {
  loading.value = true
  try {
    rows.value = await myDrills()
  } finally {
    loading.value = false
  }
}
async function sign(r) {
  signing.value = r.drill.id
  try {
    await signDrill(r.drill.id)
    ElMessage.success('已签到')
    load()
  } finally {
    signing.value = null
  }
}
onMounted(load)
</script>

<style scoped>
.tip { margin: 4px 2px 10px; }
.sign { margin-top: 8px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.btn { width: 100%; }
.ok { color: #52c41a; font-weight: 600; }
.bad { color: #f56c6c; }
.grade { color: #409eff; font-size: 13px; }
</style>
