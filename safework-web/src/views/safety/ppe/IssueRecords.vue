<template>
  <div>
    <div class="toolbar">
      <el-input v-if="canEdit" v-model="query.keyword" placeholder="姓名 / 用品" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option label="在用" value="IN_USE" />
        <el-option label="已更换" value="REPLACED" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="issueDate" label="发放日期" width="110" />
      <el-table-column v-if="canEdit" prop="userName" label="领用人" width="150" show-overflow-tooltip />
      <el-table-column prop="itemName" label="用品" min-width="200" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="70" />
      <el-table-column prop="expireDate" label="到期日期" width="110" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag size="small" :type="row.status === 'IN_USE' ? 'success' : 'info'">{{ row.status === 'IN_USE' ? '在用' : '已更换' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="issuerName" label="发放人" width="130" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ppeIssuePage } from '@/api'

defineProps({ canEdit: Boolean })
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '', status: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await ppeIssuePage(params)
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
defineExpose({ load })
</script>
