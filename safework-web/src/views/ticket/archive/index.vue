<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="search">
        <el-radio-button value="COMPLETED" label="COMPLETED">已完成</el-radio-button>
        <el-radio-button value="CANCELLED" label="CANCELLED">已取消</el-radio-button>
      </el-radio-group>
      <el-input v-model="input" clearable placeholder="票号 / 名称 / 地点" style="width:220px" @keyup.enter="search" @clear="search"/>
      <el-select v-model="kind" clearable placeholder="作业类型" style="width:150px" @change="search">
        <el-option v-for="(type, key) in TICKET_TYPES" :key="key" :label="type.label" :value="key"/>
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer"/>
      <el-button v-perm="'ticket:export'" icon="Download" :loading="exporting" @click="exportRows">导出台账</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe empty-text="当前条件下没有归档作业票">
      <el-table-column prop="ticketNo" label="作业票号" min-width="160"/>
      <el-table-column prop="title" label="作业名称" min-width="160"/>
      <el-table-column label="类型" width="120"><template #default="{row}">{{ TICKET_TYPES[row.ticketType]?.label }}</template></el-table-column>
      <el-table-column prop="location" label="地点" min-width="130"/>
      <el-table-column prop="applicantName" label="负责人" min-width="110"/>
      <el-table-column prop="guardianName" label="监护人" min-width="110"/>
      <el-table-column prop="planStartTime" label="计划开始" min-width="165"/>
      <el-table-column v-if="status === 'COMPLETED'" label="实际完工" min-width="165">
        <template #default="{row}">{{ row.actualEndTime || '未记录' }}</template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="95">
        <template #default="{row}"><el-button link type="primary" @click="$router.push({path:'/ticket/list',query:{detail:row.id}})">查看票面</el-button></template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load"/>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { pageTickets } from '@/api'
import { downloadFile } from '@/utils/download'
import { TICKET_TYPES, TICKET_STATUS } from '@/utils/dict'

const status = ref('COMPLETED')
const kind = ref('')
const input = ref('')
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const exporting = ref(false)
let requestId = 0

function filters() {
  return { status: status.value, ticketType: kind.value || undefined, keyword: keyword.value || undefined }
}

async function load() {
  const currentRequest = ++requestId
  loading.value = true
  try {
    const result = await pageTickets({ ...filters(), current: page.value, size: 20 })
    if (currentRequest !== requestId) return
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (currentRequest === requestId) {
      rows.value = []
      total.value = 0
    }
    throw error
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function search() {
  keyword.value = input.value.trim()
  page.value = 1
  return load()
}

async function exportRows() {
  exporting.value = true
  try {
    await downloadFile('/ticket/export', filters(), '作业归档台账-' + TICKET_STATUS[status.value].label + '.xlsx')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>
