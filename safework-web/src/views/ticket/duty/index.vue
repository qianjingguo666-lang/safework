<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="search">
        <el-radio-button value="IN_PROGRESS" label="IN_PROGRESS">作业中</el-radio-button>
        <el-radio-button label="PAUSED">已暂停</el-radio-button>
        <el-radio-button label="RESUME_PENDING">待复工审核</el-radio-button>
        <el-radio-button value="APPROVED" label="APPROVED">待开工</el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" clearable placeholder="编号 / 名称 / 地点" style="width:220px"
        @keyup.enter="search" @clear="search" />
      <el-select v-model="kind" clearable placeholder="作业类型" style="width:150px" @change="search">
        <el-option v-for="(item,key) in TICKET_TYPES" :key="key" :value="key" :label="item.label" />
      </el-select>
      <el-button @click="search">查询</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe empty-text="当前条件下没有作业票">
      <el-table-column prop="ticketNo" label="票号" min-width="150"/>
      <el-table-column prop="title" label="作业名称" min-width="160"/>
      <el-table-column prop="location" label="地点" min-width="120"/>
      <el-table-column prop="guardianName" label="监护人" min-width="110"/>
      <el-table-column prop="applicantName" label="负责人" min-width="110"/>
      <el-table-column prop="planStartTime" label="计划开始" min-width="160"/>
      <el-table-column prop="planEndTime" label="计划结束" min-width="160"/>
      <el-table-column label="现场提示" width="130">
        <template #default="{row}">
          <el-tag :type="hint(row).type">{{ hint(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" width="140" label="操作">
        <template #default="{row}">
          <el-button link type="primary" @click="$router.push({path:'/ticket/list',query:{detail:row.id}})">查看票面</el-button>
          <el-button v-if="row.status === 'IN_PROGRESS'" link type="primary" @click="$router.push({path:'/ticket/handover',query:{ticket:row.id,title:row.title}})">交班</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="20" :total="total"
        layout="total, prev, pager, next" @current-change="load"/>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { pageTickets } from '@/api'
import { TICKET_TYPES } from '@/utils/dict'

const status = ref('IN_PROGRESS')
const keyword = ref('')
const kind = ref('')
const page = ref(1)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const now = ref(Date.now())
let timer
let request = 0

function hint(row) {
  if (row.status === 'PAUSED') return {type:'danger',label:'已暂停'}
  if (row.status === 'RESUME_PENDING') return {type:'warning',label:'待复工审核'}
  const end = row.planEndTime && Date.parse(row.planEndTime.replace(' ', 'T'))
  if (end && end <= now.value) return { type: 'danger', label: '已过计划结束时间' }
  if (row.status === 'APPROVED') return { type: 'info', label: '待开工' }
  if (end && end - now.value <= 3600000) return { type: 'warning', label: '一小时内到期' }
  return { type: 'primary', label: '作业中' }
}

async function load() {
  const id = ++request
  loading.value = true
  try {
    const result = await pageTickets({ current: page.value, size: 20, status: status.value,
      keyword: keyword.value.trim() || undefined, ticketType: kind.value || undefined })
    if (id !== request) return
    total.value = result.total
    rows.value = result.records
    if (page.value > 1 && !rows.value.length) search()
  } catch (err) {
    if (id === request) { rows.value = []; total.value = 0 }
    throw err
  } finally {
    if (id === request) loading.value = false
  }
}
function search() { page.value = 1; return load() }
onMounted(() => {
  load()
  timer = setInterval(() => { now.value = Date.now() }, 30000)
})
onUnmounted(() => clearInterval(timer))
</script>