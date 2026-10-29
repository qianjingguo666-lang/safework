<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="编号 / 名称 / 地点" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.ticketType" placeholder="作业类型" clearable style="width: 150px" @change="search">
        <el-option v-for="(v, k) in TICKET_TYPES" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
        <el-option v-for="(v, k) in TICKET_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'ticket:export'" icon="Download" :loading="exporting" @click="doExport">导出</el-button>
      <el-button v-perm="'ticket:add'" type="primary" icon="Plus" @click="openCreate">新建作业票</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe @row-click="openDetail">
      <el-table-column prop="ticketNo" label="编号" width="160" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ TICKET_TYPES[row.ticketType]?.label }}</template>
      </el-table-column>
      <el-table-column prop="title" label="作业名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="location" label="地点" min-width="140" show-overflow-tooltip />
      <el-table-column prop="applicantName" label="申请人" width="140" />
      <el-table-column prop="guardianName" label="监护人" width="140" />
      <el-table-column prop="planStartTime" label="计划开始" width="170" />
      <el-table-column label="状态" width="100" fixed="right">
        <template #default="{ row }">
          <el-tag :type="TICKET_STATUS[row.status]?.type" effect="light">{{ TICKET_STATUS[row.status]?.label }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <TicketForm ref="formRef" @saved="onSaved" />
    <TicketDetail ref="detailRef" @changed="load" @edit="openEdit" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { downloadFile } from '@/utils/download'
import { pageTickets } from '@/api'
import { TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'
import TicketForm from './TicketForm.vue'
import TicketDetail from './TicketDetail.vue'

const route = useRoute()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', ticketType: '', status: route.query.status || '' })
const formRef = ref()
const detailRef = ref()

async function load() {
  loading.value = true
  try {
    // 空字符串不传，避免后端把 "" 转枚举失败
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await pageTickets(params)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}

const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([k, v]) => v !== '' && v != null && !['current', 'size'].includes(k)))
    await downloadFile('/ticket/export', params, '作业票台账.xlsx')
  } finally {
    exporting.value = false
  }
}
const openCreate = () => formRef.value.open()
const openEdit = (ticket) => formRef.value.open(ticket)
const openDetail = (row) => detailRef.value.open(row.id)
function onSaved(id) {
  load()
  detailRef.value.open(id)
}

onMounted(() => {
  load()
  if (route.query.create) openCreate()
})
// 从消息点进来：/ticket/list?detail=ID 直接打开详情（已经在这个页面时也要响应）
const openFromQuery = () => { if (route.query.detail) detailRef.value?.open(route.query.detail) }
onMounted(openFromQuery)
watch(() => route.query.detail, openFromQuery)
</script>

<style scoped>
:deep(.el-table__row) { cursor: pointer; }
</style>
