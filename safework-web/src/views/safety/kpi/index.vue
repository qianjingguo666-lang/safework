<template>
  <div class="page-card" v-loading="loading">
    <div class="toolbar">
      <el-select v-model="year" style="width: 120px" @change="load">
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>
      <span v-if="r" class="muted">统计到 {{ r.asOf }}，数据由各模块自动统计，不用手填</span>
      <div class="spacer" />
      <el-button v-if="canEdit" type="primary" icon="Setting" @click="openTargets">设置年度目标</el-button>
    </div>
    <el-alert v-if="r?.defaultTargets" type="info" :closable="false" show-icon class="tip"
              title="这一年还没有设置目标，下面用的是系统默认目标。点右上角“设置年度目标”可以修改目标值和权重。" />

    <template v-if="r">
      <div class="summary">
        <div class="score">
          <div class="num" :class="scoreClass">{{ r.totalScore ?? '-' }}</div>
          <div class="muted">综合得分（百分制）</div>
        </div>
        <div class="lights">
          <div class="light green"><b>{{ r.green }}</b><span>达标</span></div>
          <div class="light yellow"><b>{{ r.yellow }}</b><span>接近目标</span></div>
          <div class="light red"><b>{{ r.red }}</b><span>未达标</span></div>
        </div>
        <div class="explain muted">
          比例类指标：达到目标亮绿灯，达到目标的 90% 亮黄灯，否则亮红灯；次数类（事故起数）不超过目标为绿灯。<br />
          得分 = 权重 ×（实际 / 目标，最多 1）；没有数据的指标不计分。
        </div>
      </div>

      <div class="grid">
        <div v-for="i in r.items" :key="i.code" class="card" :class="[i.light, { on: sel === i.code }]" @click="sel = i.code">
          <div class="c-head">
            <span class="dot" />
            <b>{{ i.label }}</b>
            <span class="w muted">权重 {{ i.weight }}</span>
          </div>
          <div class="c-val">
            <span class="actual">{{ i.actual == null ? '暂无数据' : fmt(i.actual) + i.unit }}</span>
            <span class="muted">目标 {{ i.direction === 'LOWER' ? '≤' : '≥' }} {{ fmt(i.target) }}{{ i.unit }}</span>
          </div>
          <el-progress v-if="i.direction === 'HIGHER' && i.actual != null" :percentage="Math.min(100, i.actual)" :show-text="false" :stroke-width="6"
                       :color="LIGHT_COLORS[i.light]" />
          <div class="c-foot muted">
            <span v-if="i.denominator != null">{{ i.numerator }} / {{ i.denominator }}</span>
            <span>来自{{ i.source }}{{ i.snapshot ? '（当前）' : '' }}</span>
            <span v-if="i.score != null">得分 {{ i.score }}</span>
          </div>
        </div>
      </div>

      <div v-if="cur" class="trend">
        <div class="t-head">
          <b>{{ cur.label }} · 每月趋势</b>
          <span class="muted">{{ cur.formula }}</span>
        </div>
        <EChart v-if="!cur.snapshot" :option="trendOption" height="260px" />
        <el-empty v-else :image-size="60" description="这个指标只统计当前状态，没有按月趋势" />
      </div>
    </template>

    <el-dialog v-model="tVisible" :title="`${year} 年安全目标`" width="620px">
      <el-table :data="targets" size="small" border>
        <el-table-column prop="label" label="指标" min-width="160" />
        <el-table-column label="目标" width="190">
          <template #default="{ row }">
            {{ row.direction === 'LOWER' ? '不超过' : '不低于' }}
            <el-input-number v-model="row.target" :min="0" :max="row.direction === 'HIGHER' ? 100 : 9999" :precision="row.direction === 'HIGHER' ? 1 : 0"
                             :controls="false" size="small" style="width: 70px" /> {{ row.unit }}
          </template>
        </el-table-column>
        <el-table-column label="权重" width="120">
          <template #default="{ row }"><el-input-number v-model="row.weight" :min="0" :max="100" size="small" style="width: 95px" /></template>
        </el-table-column>
      </el-table>
      <div class="sum" :class="{ bad: weightSum !== 100 }">权重合计 {{ weightSum }}（要等于 100）</div>
      <template #footer>
        <el-button @click="tVisible = false">取消</el-button>
        <el-button type="primary" :disabled="weightSum !== 100" :loading="saving" @click="saveTargets">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { kpiReport, kpiTargets, saveKpiTargets } from '@/api'
import EChart from '@/components/EChart.vue'

const LIGHT_COLORS = { GREEN: '#52c41a', YELLOW: '#faad14', RED: '#f5222d', NO_DATA: '#c0c4cc' }
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('kpi:edit'))
const now = new Date().getFullYear()
const years = [now - 1, now, now + 1]
const year = ref(now)
const r = ref(null)
const sel = ref('HAZARD_ONTIME')
const loading = ref(false)
const tVisible = ref(false)
const saving = ref(false)
const targets = ref([])
const cur = computed(() => r.value?.items.find((i) => i.code === sel.value))
const weightSum = computed(() => targets.value.reduce((s, t) => s + (t.weight || 0), 0))
const scoreClass = computed(() => (r.value?.totalScore == null ? '' : r.value.totalScore >= 90 ? 'green' : r.value.totalScore >= 75 ? 'yellow' : 'red'))
const fmt = (v) => (v == null ? '-' : Number.isInteger(v) ? String(v) : String(Math.round(v * 10) / 10))

const trendOption = computed(() => {
  const i = cur.value
  const months = Array.from({ length: 12 }, (_, k) => `${k + 1}月`)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['实际', '目标'], right: 10 },
    grid: { left: 40, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', name: i.unit, max: i.direction === 'HIGHER' ? 100 : null, minInterval: i.direction === 'LOWER' ? 1 : null },
    series: [
      { name: '实际', type: i.direction === 'LOWER' ? 'bar' : 'line', data: i.months, smooth: true, connectNulls: false, itemStyle: { color: '#1677ff' } },
      { name: '目标', type: 'line', data: months.map(() => i.target), symbol: 'none', lineStyle: { type: 'dashed', color: '#f5222d' } }
    ]
  }
})

async function load() {
  loading.value = true
  try {
    r.value = await kpiReport(year.value)
  } finally {
    loading.value = false
  }
}
async function openTargets() {
  targets.value = await kpiTargets(year.value)
  tVisible.value = true
}
async function saveTargets() {
  saving.value = true
  try {
    await saveKpiTargets({ year: year.value, items: targets.value.map((t) => ({ code: t.code, target: t.target, weight: t.weight })) })
    ElMessage.success('已保存，得分已按新目标重新计算')
    tVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.tip { margin-bottom: 12px; }
.summary { display: flex; gap: 24px; align-items: center; flex-wrap: wrap; padding: 14px 18px; border: 1px solid #ebeef5; border-radius: 10px; margin-bottom: 14px; }
.score { text-align: center; min-width: 120px; }
.num { font-size: 40px; font-weight: 700; line-height: 1.1; }
.num.green { color: #52c41a; }
.num.yellow { color: #faad14; }
.num.red { color: #f5222d; }
.lights { display: flex; gap: 12px; }
.light { display: flex; flex-direction: column; align-items: center; padding: 6px 14px; border-radius: 8px; }
.light b { font-size: 22px; }
.light span { font-size: 12px; }
.light.green { background: #f6ffed; color: #389e0d; }
.light.yellow { background: #fffbe6; color: #d48806; }
.light.red { background: #fff1f0; color: #cf1322; }
.explain { flex: 1; min-width: 260px; line-height: 1.7; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
.card { border: 1px solid #ebeef5; border-top: 4px solid #c0c4cc; border-radius: 8px; padding: 10px 14px; cursor: pointer; transition: box-shadow 0.2s; }
.card:hover, .card.on { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1); }
.card.on { border-color: #1677ff; }
.card.GREEN { border-top-color: #52c41a; }
.card.YELLOW { border-top-color: #faad14; }
.card.RED { border-top-color: #f5222d; }
.c-head { display: flex; align-items: center; gap: 6px; }
.dot { width: 10px; height: 10px; border-radius: 50%; background: #c0c4cc; }
.GREEN .dot { background: #52c41a; }
.YELLOW .dot { background: #faad14; }
.RED .dot { background: #f5222d; }
.w { margin-left: auto; }
.c-val { display: flex; align-items: baseline; justify-content: space-between; margin: 8px 0 6px; }
.actual { font-size: 24px; font-weight: 600; }
.c-foot { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 6px; }
.trend { margin-top: 16px; border: 1px solid #ebeef5; border-radius: 10px; padding: 12px 16px; }
.t-head { display: flex; gap: 12px; align-items: baseline; flex-wrap: wrap; margin-bottom: 6px; }
.sum { margin-top: 10px; color: #52c41a; }
.sum.bad { color: #f5222d; }
</style>
