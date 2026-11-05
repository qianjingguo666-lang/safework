<template>
  <div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column label="事故" width="220" show-overflow-tooltip><template #default="{ row }">{{ row.eventTitle }}</template></el-table-column>
      <el-table-column prop="content" label="整改措施" min-width="260" show-overflow-tooltip />
      <el-table-column label="期限" width="110"><template #default="{ row }">{{ row.deadline }}</template></el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag size="small" :type="ACCIDENT_MEASURE_STATES[row.state]?.type">{{ ACCIDENT_MEASURE_STATES[row.state]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="落实情况" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.doneNote || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }"><el-button v-if="row.status === 'OPEN'" link type="primary" @click="doneRef.open(row)">填写落实情况</el-button></template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="没有分配给你的事故整改措施" />
    <DoneDialog ref="doneRef" @done="afterDone" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { myAccidentMeasures } from '@/api'
import { ACCIDENT_MEASURE_STATES } from '@/utils/dict'
import DoneDialog from './DoneDialog.vue'

const emit = defineEmits(['changed'])
const loading = ref(false)
const rows = ref([])
const doneRef = ref()
async function load() {
  loading.value = true
  try {
    rows.value = await myAccidentMeasures()
  } finally {
    loading.value = false
  }
}
function afterDone() {
  load()
  emit('changed')
}
onMounted(load)
</script>
