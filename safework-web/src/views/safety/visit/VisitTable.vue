<template>
  <el-table v-loading="loading" :data="rows" stripe @row-click="(r) => emit('open', r.pass.id)">
    <el-table-column label="日期" width="105"><template #default="{ row }">{{ row.pass.planDate }}</template></el-table-column>
    <el-table-column label="类型" width="110">
      <template #default="{ row }"><el-tag size="small" :type="VISIT_TYPES[row.pass.visitType]?.type">{{ VISIT_TYPES[row.pass.visitType]?.label }}</el-tag></template>
    </el-table-column>
    <el-table-column label="来访人 / 驾驶员" width="150"><template #default="{ row }">{{ row.pass.visitorName }}<span class="muted">（{{ row.pass.peopleCount }} 人）</span></template></el-table-column>
    <el-table-column label="单位 / 事由" min-width="240" show-overflow-tooltip>
      <template #default="{ row }">{{ row.pass.company }}<span class="muted"> · {{ row.pass.cargoName ? `${row.pass.cargoName} ${row.pass.cargoQty} 吨` : row.pass.purpose }}</span></template>
    </el-table-column>
    <el-table-column label="车牌" width="100"><template #default="{ row }">{{ row.pass.plateNo || '-' }}</template></el-table-column>
    <el-table-column label="接待人" width="140" show-overflow-tooltip><template #default="{ row }">{{ row.pass.hostName }}</template></el-table-column>
    <el-table-column label="状态" width="170">
      <template #default="{ row }">
        <el-tag size="small" :type="VISIT_STATUS[row.pass.status]?.type">{{ VISIT_STATUS[row.pass.status]?.label }}</el-tag>
        <el-tag v-if="row.overstay" size="small" type="danger" class="ml">超时未离厂</el-tag>
        <el-tooltip v-if="row.problems.length" :content="row.problems.join('；')"><el-tag size="small" type="danger" effect="dark" class="ml">证件不符</el-tag></el-tooltip>
        <span v-if="row.pass.enterTime" class="muted ml">{{ hm(row.pass.enterTime) }}{{ row.pass.leaveTime ? '-' + hm(row.pass.leaveTime) : '' }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { VISIT_STATUS, VISIT_TYPES, hm } from './visit'

defineProps({ rows: { type: Array, default: () => [] }, loading: Boolean })
const emit = defineEmits(['open'])
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.ml { margin-left: 4px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
