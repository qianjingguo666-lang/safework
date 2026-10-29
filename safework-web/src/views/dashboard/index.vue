<template>
  <div v-loading="loading" class="dash">
    <div class="page-card bar">
      <div>
        <h3>数据看板</h3>
        <div class="muted">{{ store.tenantName }} · 统计时间 {{ updatedAt }}</div>
      </div>
      <el-radio-group v-model="days" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="d">
      <!-- 指标卡：一眼看到现在要关注什么 -->
      <div class="kpis">
        <div v-for="k in kpis" :key="k.label" class="page-card kpi" :class="{ clickable: k.to }" @click="k.to && $router.push(k.to)">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span v-if="k.unit" class="unit">{{ k.unit }}</span></div>
          <div class="kpi-sub" :class="k.alert ? 'alert' : ''">
            <el-icon v-if="k.alert"><WarningFilled /></el-icon>{{ k.sub }}
          </div>
        </div>
      </div>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="16">
          <div class="page-card chart-card">
            <div class="card-head">
              <span>每天新建的作业票</span>
              <el-button link type="primary" @click="table.ticket = !table.ticket">{{ table.ticket ? '看图表' : '看表格' }}</el-button>
            </div>
            <EChart v-if="!table.ticket" :option="ticketTrendOption" />
            <el-table v-else :data="ticketTable" size="small" height="280">
              <el-table-column prop="date" label="日期" width="90" />
              <el-table-column v-for="name in ticketTypes" :key="name" :prop="name" :label="name" align="right" />
            </el-table>
          </div>
        </el-col>
        <el-col :xs="24" :lg="8">
          <div class="page-card chart-card">
            <div class="card-head"><span>作业票状态（统计周期内）</span></div>
            <EChart v-if="d.ticketStatus.length" :option="statusOption" />
            <el-empty v-else description="这段时间没有作业票" :image-size="80" />
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="12">
          <div class="page-card chart-card">
            <div class="card-head">
              <span>隐患上报与闭环</span>
              <el-button link type="primary" @click="table.hazard = !table.hazard">{{ table.hazard ? '看图表' : '看表格' }}</el-button>
            </div>
            <EChart v-if="!table.hazard" :option="hazardTrendOption" />
            <el-table v-else :data="hazardTable" size="small" height="280">
              <el-table-column prop="date" label="日期" width="90" />
              <el-table-column prop="reported" label="新上报" align="right" />
              <el-table-column prop="closed" label="闭环" align="right" />
            </el-table>
          </div>
        </el-col>
        <el-col :xs="24" :lg="6">
          <div class="page-card chart-card">
            <div class="card-head"><span>隐患类别（全部）</span></div>
            <EChart v-if="d.hazardCategory.length" :option="categoryOption" />
            <el-empty v-else description="还没有隐患" :image-size="80" />
          </div>
        </el-col>
        <el-col :xs="24" :lg="6">
          <div class="page-card chart-card">
            <div class="card-head"><span>受限空间风险等级</span></div>
            <EChart :option="riskOption" />
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { dashboardOverview } from '@/api'
import { useUserStore } from '@/stores/user'
import EChart from '@/components/EChart.vue'

const store = useUserStore()

// 配色：分类色按固定顺序分配（同一个作业类型在所有图里颜色不变），风险等级用状态色并且配文字标签
const SERIES = ['#2a78d6', '#eb6834', '#1baf7a']
const STATUS = { 高风险: '#d03b3b', 中风险: '#fab219', 低风险: '#0ca30c' }
const INK = { secondary: '#52514e', muted: '#898781', grid: '#e1e0d9', axis: '#c3c2b7' }

const loading = ref(false)
const days = ref(30)
const d = ref(null)
const updatedAt = ref('')
const table = reactive({ ticket: false, hazard: false })

async function load() {
  loading.value = true
  try {
    d.value = await dashboardOverview(days.value)
    updatedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } finally {
    loading.value = false
  }
}

// 后端 Long 统一转成字符串返回，画图前转回数字
const nums = (arr) => (arr || []).map(Number)

const kpis = computed(() => {
  const k = d.value.kpi
  return [
    { label: `近 ${days.value} 天作业票`, value: k.ticketTotal, sub: `待审批 ${k.ticketPending} 张`, alert: k.ticketPending > 0, to: '/ticket/list?status=PENDING' },
    { label: '正在作业', value: k.ticketInProgress, sub: `涉及受限空间 ${k.spaceWorking} 个`, to: '/ticket/list?status=IN_PROGRESS' },
    { label: '未闭环隐患', value: k.hazardOpen, sub: `其中重大隐患 ${k.hazardMajorOpen} 条`, alert: k.hazardMajorOpen > 0, to: '/safety/hazard' },
    { label: '逾期未整改', value: k.hazardOverdue, sub: k.hazardOverdue ? '请督促整改责任人' : '没有逾期', alert: k.hazardOverdue > 0, to: '/safety/hazard' },
    { label: '整改及时率', value: k.rectifyOnTimeRate ?? '-', unit: k.rectifyOnTimeRate != null ? '%' : '', sub: '已闭环隐患中按期完成的比例' },
    { label: '受限空间', value: k.spaceTotal, sub: `高风险 ${k.spaceHighRisk} 个`, to: '/safety/space' }
  ]
})

const baseGrid = { left: 8, right: 16, top: 36, bottom: 8, containLabel: true }
const axisCommon = {
  axisLine: { lineStyle: { color: INK.axis } },
  axisTick: { show: false },
  axisLabel: { color: INK.muted }
}
const valueAxis = { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: INK.grid } }, axisLabel: { color: INK.muted } }
const tooltipAxis = { trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: INK.axis } } }
const legend = { top: 0, left: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 4, textStyle: { color: INK.secondary } }
const line = (name, data, color) => ({
  name, type: 'line', data, smooth: false, symbol: 'circle', symbolSize: 8, showSymbol: false,
  lineStyle: { width: 2, color }, itemStyle: { color, borderColor: '#fff', borderWidth: 2 }
})

const ticketTypes = computed(() => Object.keys(d.value.ticketTrend))
const ticketTrendOption = computed(() => ({
  grid: baseGrid,
  legend,
  tooltip: tooltipAxis,
  xAxis: { type: 'category', data: d.value.dates, boundaryGap: false, ...axisCommon },
  yAxis: valueAxis,
  series: ticketTypes.value.map((name, i) => line(name, nums(d.value.ticketTrend[name]), SERIES[i]))
}))
const ticketTable = computed(() => d.value.dates.map((date, i) => {
  const row = { date }
  ticketTypes.value.forEach((name) => { row[name] = Number(d.value.ticketTrend[name][i]) })
  return row
}).reverse())

// 横向条形图：标签在左边，数值直接写在条末端
const hbar = (items, colorOf) => ({
  grid: { left: 8, right: 36, top: 8, bottom: 8, containLabel: true },
  tooltip: { trigger: 'item' },
  xAxis: { ...valueAxis, show: false },
  yAxis: { type: 'category', inverse: true, data: items.map((x) => x.name), ...axisCommon, axisLine: { show: false }, axisLabel: { color: INK.secondary } },
  series: [{
    type: 'bar', barMaxWidth: 20, data: items.map((x) => ({ value: Number(x.value), itemStyle: { color: colorOf(x) } })),
    itemStyle: { borderRadius: [0, 4, 4, 0] },
    label: { show: true, position: 'right', color: INK.secondary }
  }]
})
const statusOption = computed(() => hbar(d.value.ticketStatus, () => SERIES[0]))
const riskOption = computed(() => hbar(d.value.spaceRisk, (x) => STATUS[x.name]))

const hazardTrendOption = computed(() => ({
  grid: baseGrid,
  legend,
  tooltip: tooltipAxis,
  xAxis: { type: 'category', data: d.value.dates, boundaryGap: false, ...axisCommon },
  yAxis: valueAxis,
  series: [line('新上报', nums(d.value.hazardReported), SERIES[0]), line('闭环', nums(d.value.hazardClosed), SERIES[2])]
}))
const hazardTable = computed(() => d.value.dates.map((date, i) => ({
  date, reported: Number(d.value.hazardReported[i]), closed: Number(d.value.hazardClosed[i])
})).reverse())

// 堆叠条形：未闭环 + 已闭环，段与段之间留 2px 白色间隔
const categoryOption = computed(() => {
  const cats = d.value.hazardCategory
  const seg = (name, key, color, last) => ({
    name, type: 'bar', stack: 'total', barMaxWidth: 20, data: cats.map((c) => Number(c[key])),
    itemStyle: { color, borderColor: '#fff', borderWidth: 2, borderRadius: last ? [0, 4, 4, 0] : 0 },
    label: { show: true, position: 'inside', color: '#fff', formatter: (p) => (p.value ? p.value : '') }
  })
  return {
    grid: { left: 8, right: 16, top: 36, bottom: 8, containLabel: true },
    legend,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { ...valueAxis, show: false },
    yAxis: { type: 'category', inverse: true, data: cats.map((c) => c.name), ...axisCommon, axisLine: { show: false }, axisLabel: { color: INK.secondary } },
    series: [seg('未闭环', 'open', SERIES[1], false), seg('已闭环', 'closed', SERIES[2], true)]
  }
})

onMounted(load)
</script>

<style scoped>
.dash { display: flex; flex-direction: column; gap: 16px; }
.bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.bar h3 { margin: 0 0 4px; }
.muted { color: #898781; font-size: 12px; }
.kpis { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; }
.kpi { padding: 16px; }
.kpi.clickable { cursor: pointer; transition: box-shadow .2s; }
.kpi.clickable:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, .08); }
.kpi-label { color: #52514e; font-size: 13px; }
.kpi-value { font-size: 30px; font-weight: 600; color: #0b0b0b; margin: 6px 0 4px; }
.unit { font-size: 14px; margin-left: 2px; color: #52514e; font-weight: normal; }
.kpi-sub { color: #898781; font-size: 12px; display: flex; align-items: center; gap: 4px; }
.kpi-sub.alert { color: #d03b3b; }
.chart-card { margin-bottom: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; margin-bottom: 8px; }
.el-row { margin-bottom: -16px; }
@media (max-width: 1280px) { .kpis { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 640px) { .kpis { grid-template-columns: repeat(2, 1fr); gap: 10px; } .kpi-value { font-size: 24px; } }
</style>
