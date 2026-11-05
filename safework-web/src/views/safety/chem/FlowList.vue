<template>
  <div>
    <div class="toolbar">
      <el-select v-model="query.storeId" placeholder="存储单元" clearable style="width: 150px" @change="search">
        <el-option v-for="s in stores" :key="s.store.id" :label="s.store.name" :value="s.store.id" />
      </el-select>
      <el-select v-model="query.flowType" placeholder="类型" clearable style="width: 100px" @change="search">
        <el-option v-for="(v, k) in CHEM_FLOW_TYPES" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期"
                      style="width: 240px" :popper-options="PICKER_POPPER" @change="search" />
      <el-button icon="Search" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="时间" width="160"><template #default="{ row }">{{ row.createTime?.slice(0, 16) }}</template></el-table-column>
      <el-table-column prop="storeName" label="单元" width="120" />
      <el-table-column prop="chemName" label="危化品" width="120" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }"><el-tag size="small" :type="CHEM_FLOW_TYPES[row.flowType]?.type">{{ CHEM_FLOW_TYPES[row.flowType]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="数量（吨）" width="100">
        <template #default="{ row }">{{ row.flowType === 'OUT' ? '-' : row.flowType === 'IN' || Number(row.qty) > 0 ? '+' : '' }}{{ ton(row.qty) }}</template>
      </el-table-column>
      <el-table-column label="结存（吨）" width="100"><template #default="{ row }">{{ ton(row.balance) }}</template></el-table-column>
      <el-table-column prop="purpose" label="用途 / 说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="operatorName" label="经办人" width="140" show-overflow-tooltip />
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { chemFlows, chemStores } from '@/api'
import { CHEM_FLOW_TYPES, ton } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const stores = ref([])
const range = ref(null)
const query = reactive({ current: 1, size: 20, storeId: null, flowType: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    if (range.value) Object.assign(params, { from: range.value[0], to: range.value[1] })
    const page = await chemFlows(params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
onMounted(async () => {
  load()
  stores.value = await chemStores()
})
</script>
