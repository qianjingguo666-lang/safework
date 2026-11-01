<template>
  <div ref="root" class="screen">
    <header class="top">
      <div class="brand">
        <el-icon :size="26"><Lock /></el-icon>
        <div>
          <h1>{{ store.tenantName || 'SafeWork' }} · 安全生产态势</h1>
          <p>数据每 30 秒自动刷新　最后更新 {{ updatedAt }}</p>
        </div>
      </div>
      <div class="safe-days">
        <template v-if="d && d.safeDays != null">安全运行 <b>{{ d.safeDays }}</b> 天</template>
        <template v-else-if="d">暂无事故记录</template>
      </div>
      <div class="right">
        <div class="clock">{{ clock }}</div>
        <el-button text class="tool" :icon="fullscreen ? 'Close' : 'FullScreen'" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</el-button>
        <el-button text class="tool" icon="Back" @click="$router.push('/home')">返回系统</el-button>
      </div>
    </header>

    <div v-if="d?.responding.length" class="alarm">
      <el-icon class="blink" :size="22"><AlarmClock /></el-icon>
      <span v-for="e in d.responding" :key="e.id">
        【{{ e.level }}】{{ e.title }} · {{ e.location }} · 现场指挥 {{ e.commanderName }} · {{ e.responseTime }} 启动响应
      </span>
    </div>

    <div v-if="d" class="kpis">
      <div v-for="k in kpis" :key="k.label" class="kpi" :class="k.cls">
        <b>{{ k.value }}</b>
        <span>{{ k.label }}</span>
      </div>
    </div>

    <main v-if="d" class="grid">
      <section class="panel">
        <h3>风险四色分布<small v-if="d.riskCheckOverdue">巡检逾期 {{ d.riskCheckOverdue }} 个</small></h3>
        <div class="colors">
          <div v-for="c in colorRows" :key="c.key" class="color-row">
            <span class="dot" :style="{ background: c.color }" />
            <span class="name">{{ c.label }}</span>
            <div class="bar"><i :style="{ width: c.pct + '%', background: c.color }" /></div>
            <b>{{ c.count }}</b>
          </div>
        </div>
        <h3>到期 / 逾期预警</h3>
        <div v-if="!d.warnings.length" class="empty">没有预警，一切正常</div>
        <ul class="warnings">
          <li v-for="w in d.warnings" :key="w.label" :class="w.level" @click="$router.push(w.link)">
            <span>{{ w.label }}</span><b>{{ w.count }}</b>
          </li>
        </ul>
      </section>

      <section class="panel center">
        <h3>正在作业<small>{{ d.working.length }} 张作业票</small></h3>
        <div class="table-wrap">
          <table v-if="d.working.length">
            <thead><tr><th>类型</th><th>作业</th><th>地点</th><th>监护人</th><th>开始</th><th>计划结束</th></tr></thead>
            <tbody>
              <tr v-for="w in d.working" :key="w.id" :class="{ over: w.overtime }">
                <td><span class="type">{{ w.type }}</span></td>
                <td>{{ w.title }}<div v-if="w.contractorName" class="sub">承包商：{{ w.contractorName }}</div></td>
                <td>{{ w.location }}</td>
                <td>{{ w.guardianName }}</td>
                <td>{{ w.startTime }}</td>
                <td>{{ w.planEndTime }}<span v-if="w.overtime" class="tag-over">超时</span></td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty big">当前没有进行中的作业</div>
        </div>
        <h3>近 14 天隐患上报 / 闭环</h3>
        <EChart :option="trendOption" height="230px" />
      </section>

      <section class="panel">
        <h3>最近动态</h3>
        <div v-if="!d.feed.length" class="empty">暂无动态</div>
        <ul class="feed">
          <li v-for="(f, i) in d.feed" :key="i" :class="f.kind">
            <span class="time">{{ f.time }}</span>
            <span class="text">{{ f.text }}</span>
          </li>
        </ul>
      </section>
    </main>
    <div v-else class="loading">{{ error || '加载中…' }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { screenOverview } from '@/api'
import EChart from '@/components/EChart.vue'

const store = useUserStore()
const root = ref()
const d = ref(null)
const error = ref('')
const updatedAt = ref('-')
const clock = ref('')
const fullscreen = ref(false)
const COLORS = [
  { key: 'MAJOR', label: '重大风险', color: '#f5222d' },
  { key: 'HIGH', label: '较大风险', color: '#fa8c16' },
  { key: 'GENERAL', label: '一般风险', color: '#fadb14' },
  { key: 'LOW', label: '低风险', color: '#1890ff' }
]

const kpis = computed(() => {
  const k = d.value.kpi
  return [
    { label: '作业中', value: k.ticketInProgress, cls: 'blue' },
    { label: '待审批作业票', value: k.ticketPending, cls: k.ticketPending ? 'yellow' : '' },
    { label: '今日新增作业票', value: k.ticketToday, cls: '' },
    { label: '未闭环隐患', value: k.hazardOpen, cls: k.hazardOpen ? 'yellow' : 'green' },
    { label: '重大隐患', value: k.hazardMajorOpen, cls: k.hazardMajorOpen ? 'red' : 'green' },
    { label: '整改逾期', value: k.hazardOverdue, cls: k.hazardOverdue ? 'red' : 'green' },
    { label: '应急响应中', value: k.emergencyResponding, cls: k.emergencyResponding ? 'red blink-box' : 'green' }
  ]
})
const colorRows = computed(() => {
  const total = Math.max(1, ...COLORS.map((c) => d.value.riskColors[c.key] || 0))
  return COLORS.map((c) => ({ ...c, count: d.value.riskColors[c.key] || 0, pct: ((d.value.riskColors[c.key] || 0) * 100) / total }))
})
const axis = { axisLine: { lineStyle: { color: '#3a4a6b' } }, axisLabel: { color: '#8ea0c4' }, splitLine: { lineStyle: { color: '#1e2a44' } } }
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['上报', '闭环'], textStyle: { color: '#8ea0c4' }, top: 0 },
  grid: { left: 36, right: 16, top: 30, bottom: 24 },
  xAxis: { type: 'category', data: d.value.hazardTrend.dates, ...axis },
  yAxis: { type: 'value', minInterval: 1, ...axis },
  series: [
    { name: '上报', type: 'line', smooth: true, data: d.value.hazardTrend.reported, itemStyle: { color: '#faad14' }, areaStyle: { opacity: 0.12 } },
    { name: '闭环', type: 'line', smooth: true, data: d.value.hazardTrend.closed, itemStyle: { color: '#52c41a' }, areaStyle: { opacity: 0.12 } }
  ]
}))

async function load() {
  try {
    d.value = await screenOverview()
    error.value = ''
    updatedAt.value = new Date().toTimeString().slice(0, 8)
  } catch (e) {
    error.value = '数据加载失败，30 秒后自动重试'
  }
}
function tick() {
  const n = new Date()
  const w = '日一二三四五六'[n.getDay()]
  clock.value = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')} 星期${w} ${n.toTimeString().slice(0, 8)}`
}
function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen()
  else root.value.requestFullscreen?.()
}
const onFs = () => { fullscreen.value = !!document.fullscreenElement }

let timer = null
let clockTimer = null
onMounted(() => {
  load()
  tick()
  timer = setInterval(load, 30000)
  clockTimer = setInterval(tick, 1000)
  document.addEventListener('fullscreenchange', onFs)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  clearInterval(clockTimer)
  document.removeEventListener('fullscreenchange', onFs)
})
</script>

<style scoped>
.screen { min-height: 100vh; background: radial-gradient(ellipse at top, #10224a 0%, #070d1c 70%); color: #d6e2ff; padding: 16px 20px 20px; box-sizing: border-box; font-size: 14px; }
.top { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid #1f3566; }
.brand { display: flex; align-items: center; gap: 12px; color: #5cc8ff; }
.brand h1 { margin: 0; font-size: 22px; color: #e8f1ff; letter-spacing: 2px; }
.brand p { margin: 2px 0 0; font-size: 12px; color: #6f83ad; }
.safe-days { font-size: 18px; color: #9fb4de; }
.safe-days b { font-size: 36px; color: #52c41a; margin: 0 6px; font-family: 'DIN Alternate', 'Arial Narrow', sans-serif; }
.right { display: flex; align-items: center; gap: 8px; }
.clock { font-size: 15px; color: #9fb4de; margin-right: 8px; font-variant-numeric: tabular-nums; }
.tool { color: #8ea0c4 !important; }
.alarm { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-top: 12px; padding: 10px 16px; border: 1px solid #f5222d; background: rgba(245, 34, 45, 0.15); color: #ff7875; border-radius: 6px; font-size: 16px; }
.blink { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.2; } }
.kpis { display: grid; grid-template-columns: repeat(7, 1fr); gap: 12px; margin: 14px 0; }
.kpi { padding: 14px 10px; text-align: center; border: 1px solid #1f3566; border-radius: 8px; background: rgba(16, 34, 74, 0.55); }
.kpi b { display: block; font-size: 34px; line-height: 1.2; color: #e8f1ff; font-family: 'DIN Alternate', 'Arial Narrow', sans-serif; }
.kpi span { color: #7f93bd; font-size: 13px; }
.kpi.blue b { color: #40a9ff; }
.kpi.green b { color: #52c41a; }
.kpi.yellow b { color: #faad14; }
.kpi.red b { color: #ff4d4f; }
.kpi.blink-box { animation: glow 1.2s infinite; }
@keyframes glow { 50% { box-shadow: 0 0 18px rgba(255, 77, 79, 0.7); } }
.grid { display: grid; grid-template-columns: 1fr 2fr 1fr; gap: 14px; }
.panel { border: 1px solid #1f3566; border-radius: 8px; background: rgba(10, 22, 48, 0.6); padding: 12px 16px; min-width: 0; }
.panel h3 { margin: 6px 0 12px; font-size: 15px; color: #5cc8ff; border-left: 3px solid #5cc8ff; padding-left: 8px; display: flex; justify-content: space-between; align-items: baseline; }
.panel h3 small { color: #ff7875; font-size: 12px; font-weight: normal; }
.panel h3:not(:first-child) { margin-top: 20px; }
.color-row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.color-row .name { width: 64px; color: #9fb4de; }
.bar { flex: 1; height: 10px; background: #13213f; border-radius: 5px; overflow: hidden; }
.bar i { display: block; height: 100%; border-radius: 5px; transition: width 0.6s; }
.color-row b { width: 28px; text-align: right; font-size: 18px; }
.warnings { list-style: none; padding: 0; margin: 0; }
.warnings li { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; margin-bottom: 8px; border-radius: 6px; cursor: pointer; }
.warnings li.danger { background: rgba(255, 77, 79, 0.12); color: #ff9c9e; }
.warnings li.warning { background: rgba(250, 173, 20, 0.12); color: #ffd666; }
.warnings li b { font-size: 18px; }
.table-wrap { max-height: 330px; overflow: auto; }
table { width: 100%; border-collapse: collapse; }
th { text-align: left; color: #6f83ad; font-weight: normal; padding: 6px 8px; border-bottom: 1px solid #1f3566; position: sticky; top: 0; background: #0b1833; }
td { padding: 9px 8px; border-bottom: 1px dashed #18284a; vertical-align: top; }
tr.over td { color: #ff9c9e; }
.type { display: inline-block; padding: 1px 6px; border: 1px solid #2f6fbf; border-radius: 4px; font-size: 12px; color: #69c0ff; white-space: nowrap; }
.sub { color: #6f83ad; font-size: 12px; }
.tag-over { margin-left: 6px; padding: 0 4px; border-radius: 3px; background: #a8071a; color: #fff; font-size: 12px; }
.feed { list-style: none; padding: 0; margin: 0; max-height: 640px; overflow: auto; }
.feed li { display: flex; gap: 10px; padding: 8px 0; border-bottom: 1px dashed #18284a; line-height: 1.5; }
.feed .time { color: #6f83ad; white-space: nowrap; font-size: 12px; padding-top: 2px; }
.feed li.HAZARD .text { color: #ffd666; }
.feed li.EMERGENCY .text { color: #ff7875; }
.empty { color: #52c41a; padding: 12px 0; }
.empty.big { text-align: center; padding: 50px 0; color: #6f83ad; }
.loading { text-align: center; padding-top: 30vh; color: #6f83ad; font-size: 16px; }
@media (max-width: 1100px) {
  .grid { grid-template-columns: 1fr; }
  .kpis { grid-template-columns: repeat(4, 1fr); }
  .safe-days { display: none; }
}
@media (max-width: 640px) {
  .screen { padding: 12px; }
  .top { flex-wrap: wrap; }
  .brand { flex: 1 1 100%; }
  table { min-width: 620px; }
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .brand h1 { font-size: 16px; }
  .clock { display: none; }
}
</style>
