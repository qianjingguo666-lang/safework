<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-if="platform" v-model="query.tenantCode" placeholder="企业编码" clearable style="width: 130px" @keyup.enter="search" @clear="search" />
      <el-input v-model="query.username" placeholder="用户名" clearable style="width: 140px" @keyup.enter="search" @clear="search" />
      <el-input v-model="query.ip" placeholder="IP" clearable style="width: 140px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="结果" clearable style="width: 100px" @change="search">
        <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
      </el-select>
      <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                      start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" @change="search" />
      <el-button icon="Search" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="loginTime" label="时间" width="170" />
      <el-table-column v-if="platform" label="企业编码" width="110">
        <template #default="{ row }">
          <span :class="{ muted: row.tenantId === '-1' }">{{ row.tenantCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="browser" label="浏览器" width="130" />
      <el-table-column prop="os" label="操作系统" width="130" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="msg" label="说明" min-width="220" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { pageLoginLogs, pagePlatformLoginLogs } from '@/api'

/** 企业端和平台端共用这个页面：平台端多一列“企业编码”，可以查看全部企业 */
const props = defineProps({ platform: { type: Boolean, default: false } })

const loading = ref(false)
const list = ref([])
const total = ref(0)
const range = ref([])
const query = reactive({ current: 1, size: 20, tenantCode: '', username: '', ip: '', status: null })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    if (range.value?.length === 2) {
      params.beginTime = range.value[0]
      params.endTime = range.value[1]
    }
    const page = await (props.platform ? pagePlatformLoginLogs(params) : pageLoginLogs(params))
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
onMounted(load)
</script>

<style scoped>
.muted { color: #999; }
</style>
