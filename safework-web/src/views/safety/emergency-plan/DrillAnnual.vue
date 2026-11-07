<template>
  <div>
    <div class="toolbar">
      <el-select v-model="year" style="width: 110px" @change="load">
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>
      <span v-if="a" class="muted">启用的预案 {{ a.plans }} 份：已达标 {{ a.done }} 份，<b :class="{ danger: a.missing }">还没安排够 {{ a.missing }} 份</b></span>
      <div class="spacer" />
      <span class="muted">《生产安全事故应急预案管理办法》：综合、专项预案每年至少演练 1 次，现场处置方案每半年至少 1 次</span>
    </div>
    <el-table v-loading="loading" :data="a?.rows || []" stripe>
      <el-table-column label="预案" min-width="240">
        <template #default="{ row }">
          {{ row.planCode }} {{ row.planTitle }}
          <div class="muted">{{ PLAN_TYPES[row.planType] }}</div>
          <el-tooltip v-if="row.reviseNote" :content="row.reviseNote"><el-tag size="small" type="warning">待修订</el-tag></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="要求 / 完成 / 已安排" width="150" align="center">
        <template #default="{ row }">{{ row.required }} / <b>{{ row.done }}</b> / {{ row.planned }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag size="small" :type="DRILL_YEAR_STATES[row.state].type">{{ DRILL_YEAR_STATES[row.state].label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="今年的演练" min-width="300">
        <template #default="{ row }">
          <div v-for="d in row.drills" :key="d.id" class="drill" @click="emit('open', d.id)">
            <span>{{ d.date }}</span> {{ d.title }}
            <el-tag size="small" :type="DRILL_STATUS[d.status]?.type">{{ DRILL_STATUS[d.status]?.label }}</el-tag>
            <el-tag v-if="d.grade" size="small" :type="DRILL_GRADE_TYPES[d.grade]">{{ d.grade }}</el-tag>
          </div>
          <span v-if="!row.drills.length" class="muted">还没有</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.state === 'MISSING'" link type="primary" @click="emit('plan', row.planId)">安排演练</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { drillAnnual } from '@/api'
import { DRILL_GRADE_TYPES, DRILL_STATUS, DRILL_YEAR_STATES, PLAN_TYPES } from '@/utils/dict'

defineProps({ canEdit: Boolean })
const emit = defineEmits(['open', 'plan'])
const thisYear = new Date().getFullYear()
const years = [thisYear + 1, thisYear, thisYear - 1, thisYear - 2]
const year = ref(thisYear)
const a = ref(null)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    a.value = await drillAnnual(year.value)
  } finally {
    loading.value = false
  }
}
defineExpose({ load })
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.danger { color: #f56c6c; }
.drill { cursor: pointer; padding: 2px 0; display: flex; align-items: center; gap: 6px; }
.drill:hover { color: #409eff; }
.drill span { color: #888; font-size: 12px; }
</style>
