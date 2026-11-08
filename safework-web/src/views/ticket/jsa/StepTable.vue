<template>
  <el-table :data="steps" size="small" border class="steps">
    <el-table-column label="#" width="44" align="center"><template #default="{ row, $index }">{{ row.seq || $index + 1 }}</template></el-table-column>
    <el-table-column prop="step" label="作业步骤" min-width="130" />
    <el-table-column prop="hazards" label="危害" min-width="160" />
    <el-table-column label="风险" width="110" align="center">
      <template #default="{ row }">
        <el-tag size="small" :color="RISK_LEVELS[lv(row)].color" effect="dark" class="risk">{{ row.likelihood }}×{{ row.severity }}={{ row.likelihood * row.severity }}</el-tag>
        <div class="lv">{{ RISK_LEVELS[lv(row)].label }}</div>
      </template>
    </el-table-column>
    <el-table-column label="控制措施" min-width="240"><template #default="{ row }"><span class="pre">{{ row.controls }}</span></template></el-table-column>
  </el-table>
</template>

<script setup>
import { RISK_LEVELS, riskLevel } from './jsa'

defineProps({ steps: { type: Array, default: () => [] } })
const lv = (r) => r.level || riskLevel(r.likelihood * r.severity)
</script>

<style scoped>
.risk { border: none; }
.lv { font-size: 12px; color: #888; }
.pre { white-space: pre-wrap; }
</style>
