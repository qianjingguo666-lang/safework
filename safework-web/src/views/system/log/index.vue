<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select v-model="query.module" placeholder="模块" clearable style="width: 130px" @change="search">
        <el-option v-for="m in MODULES" :key="m" :label="m" :value="m" />
      </el-select>
      <el-input v-model="query.operatorName" placeholder="操作人" clearable style="width: 150px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="结果" clearable style="width: 100px" @change="search">
        <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
      </el-select>
      <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                      start-placeholder="开始时间" end-placeholder="结束时间" style="width: 340px" @change="search" />
      <el-button icon="Search" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <div><b>方法：</b>{{ row.method }}</div>
            <div><b>请求：</b>{{ row.requestMethod }} {{ row.requestUri }}</div>
            <div><b>参数：</b><code>{{ row.requestParams || '（未记录）' }}</code></div>
            <div v-if="row.errorMsg" class="err"><b>错误：</b>{{ row.errorMsg }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="action" label="操作" width="120" />
      <el-table-column prop="operatorName" label="操作人" width="150" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ row.costMs }} ms</template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { pageOperLogs } from '@/api'

const MODULES = ['作业票', '隐患排查', '受限空间', '用户管理', '角色管理', '部门管理', '个人中心']
const loading = ref(false)
const list = ref([])
const total = ref(0)
const range = ref([])
const query = reactive({ current: 1, size: 20, module: '', operatorName: '', status: null })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    if (range.value?.length === 2) {
      params.beginTime = range.value[0]
      params.endTime = range.value[1]
    }
    const page = await pageOperLogs(params)
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
.expand { padding: 4px 48px; line-height: 2; font-size: 13px; color: #555; }
.expand code { word-break: break-all; background: #f6f8fb; padding: 2px 6px; border-radius: 4px; }
.err { color: #f56c6c; }
</style>
