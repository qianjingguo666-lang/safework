<template>
  <div class="page-card" v-loading="loading">
    <div class="toolbar">
      <el-select v-model="year" style="width: 120px" @change="load">
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>
      <span v-if="r" class="muted">统计到 {{ r.asOf }}，依据《企业安全生产费用提取和使用管理办法》（财资〔2022〕136 号）</span>
      <div class="spacer" />
      <el-button v-if="canEdit" icon="Edit" @click="planVisible = true">{{ r?.hasPlan ? '修改年度计划' : '编制年度计划' }}</el-button>
      <el-button v-if="canEdit" type="primary" icon="Plus" @click="openExpense()">登记支出</el-button>
    </div>

    <template v-if="r">
      <el-alert v-for="a in r.alerts" :key="a" :title="a" :type="r.hasPlan ? 'warning' : 'info'" :closable="false" show-icon class="tip" />

      <div class="stats">
        <div class="stat">
          <span>本年应提</span><b>{{ wan(r.plan?.accrueAmount) }}<i>万元</i></b>
          <small v-if="r.hasPlan">上年营业收入 {{ Number(r.plan.revenue).toLocaleString() }} 万元</small>
        </div>
        <div class="stat"><span>上年结转</span><b>{{ wan(r.plan?.carryOver) }}<i>万元</i></b><small>结余结转下年使用</small></div>
        <div class="stat"><span>当年可用</span><b>{{ wan(r.available) }}<i>万元</i></b><small>截至本月应已提取 {{ wan(r.accruedToDate) }} 万元</small></div>
        <div class="stat">
          <span>已使用</span><b>{{ wan(r.used) }}<i>万元</i></b>
          <small>{{ r.expenseCount }} 笔，其中隐患整改 {{ wan(r.hazardUsed) }} 万元</small>
        </div>
        <div class="stat"><span>余额</span><b :class="{ danger: r.balance < 0 }">{{ wan(r.balance) }}<i>万元</i></b><small>{{ r.balance < 0 ? '年末要补提' : '可继续使用' }}</small></div>
        <div class="stat rate">
          <span>使用率 / 时间进度</span>
          <b :class="{ warn: r.lagging }">{{ r.usageRate ?? '-' }}<i>%</i></b>
          <el-progress :percentage="Math.min(100, r.usageRate || 0)" :show-text="false" :stroke-width="6" :color="r.lagging ? '#faad14' : '#1677ff'" />
          <small>时间过去 {{ r.timeProgress }}%</small>
        </div>
      </div>

      <el-tabs v-model="tab">
        <el-tab-pane label="使用情况" name="usage">
          <div class="usage">
            <el-table :data="r.categories" size="small" class="cats" :row-class-name="({ row }) => row.state">
              <el-table-column label="使用类别（第二十二条）" min-width="220">
                <template #default="{ row }">
                  <el-tooltip :content="row.description" placement="top-start"><span class="cat">{{ row.label }}</span></el-tooltip>
                  <el-tag v-if="STATE_TAGS[row.state]" size="small" :type="STATE_TAGS[row.state].type" class="st">{{ STATE_TAGS[row.state].label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="预算（元）" width="130" align="right"><template #default="{ row }">{{ yuan(row.budget) }}</template></el-table-column>
              <el-table-column label="已用（元）" width="130" align="right">
                <template #default="{ row }"><el-link class="used" :underline="false" @click="showCat(row.code)">{{ yuan(row.used) }}</el-link></template>
              </el-table-column>
              <el-table-column label="笔数" width="60" align="center" prop="count" />
              <el-table-column label="使用率" width="150">
                <template #default="{ row }">
                  <el-progress v-if="row.rate != null" :percentage="Math.min(100, row.rate)" :format="() => row.rate + '%'"
                               :color="row.state === 'OVER' ? '#f5222d' : '#1677ff'" />
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
            </el-table>
            <div class="side">
              <div class="box">
                <b>每月累计支出 / 累计可用</b>
                <EChart :option="monthOption" height="240px" />
              </div>
              <div v-if="r.hasPlan" class="box">
                <b>本年应提怎么算的（超额累退）</b>
                <table class="calc">
                  <tr v-for="l in r.accrual.lines" :key="l.range">
                    <td>{{ l.range }}</td><td>{{ Number(l.base).toLocaleString() }} 万元 × {{ l.rate }}%</td><td class="num">{{ yuan(l.amount) }} 元</td>
                  </tr>
                  <tr class="total"><td colspan="2">合计（逐月平均提取，每月 {{ yuan(r.plan.accrueAmount / 12) }} 元）</td><td class="num">{{ yuan(r.accrual.total) }} 元</td></tr>
                </table>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="支出台账" name="ledger">
          <ExpenseList ref="ledger" :year="year" :can-edit="canEdit" @edit="openExpense" @changed="load" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <PlanDialog v-model="planVisible" :year="year" @saved="load" />
    <ExpenseDialog v-model="expVisible" :expense="editing" @saved="onExpenseSaved" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { costReport } from '@/api'
import EChart from '@/components/EChart.vue'
import ExpenseDialog from './ExpenseDialog.vue'
import ExpenseList from './ExpenseList.vue'
import PlanDialog from './PlanDialog.vue'
import { STATE_TAGS, wan, yuan } from './money'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('cost:edit'))
const now = new Date().getFullYear()
const years = [now - 2, now - 1, now, now + 1]
const year = ref(now)
const r = ref(null)
const tab = ref('usage')
const loading = ref(false)
const planVisible = ref(false)
const expVisible = ref(false)
const editing = ref(null)
const ledger = ref(null)

const monthOption = computed(() => {
  const months = r.value.months
  const w = (v) => (v == null ? null : Math.round(Number(v) / 100) / 100)
  return {
    tooltip: { trigger: 'axis', valueFormatter: (v) => (v == null ? '-' : `${v} 万元`) },
    legend: { data: ['当月支出', '累计支出', '累计可用'], right: 0, textStyle: { fontSize: 11 } },
    grid: { left: 46, right: 10, top: 34, bottom: 24 },
    xAxis: { type: 'category', data: months.map((m) => `${m.month}月`) },
    yAxis: { type: 'value', name: '万元' },
    series: [
      { name: '当月支出', type: 'bar', data: months.map((m) => w(m.used)), itemStyle: { color: '#91caff' }, barMaxWidth: 16 },
      { name: '累计支出', type: 'line', data: months.map((m) => w(m.cumUsed)), itemStyle: { color: '#1677ff' }, smooth: true },
      { name: '累计可用', type: 'line', data: months.map((m) => w(m.cumAvailable)), symbol: 'none', lineStyle: { type: 'dashed', color: '#52c41a' } }
    ]
  }
})

async function load() {
  loading.value = true
  try {
    r.value = await costReport(year.value)
  } finally {
    loading.value = false
  }
  ledger.value?.reload()
}
function openExpense(row = null) {
  editing.value = row
  expVisible.value = true
}
function onExpenseSaved() {
  tab.value = 'ledger'
  load()
}
async function showCat(code) {
  tab.value = 'ledger'
  await nextTick()
  ledger.value?.filterCategory(code)
}
onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.tip { margin-bottom: 8px; }
.stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 10px; margin: 4px 0 8px; }
.stat { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 14px; display: flex; flex-direction: column; gap: 4px; }
.stat span { color: #888; font-size: 13px; }
.stat b { font-size: 22px; }
.stat b i { font-style: normal; font-size: 12px; color: #999; margin-left: 4px; font-weight: normal; }
.stat small { color: #aaa; font-size: 12px; }
.danger { color: #f5222d; }
.warn { color: #d48806; }
.usage { display: flex; gap: 16px; flex-wrap: wrap; }
.cats { flex: 3; min-width: 560px; }
.cat { cursor: help; border-bottom: 1px dashed #ccc; }
.st { margin-left: 6px; }
.used { font-size: 12px; }
.side { flex: 2; min-width: 340px; display: flex; flex-direction: column; gap: 12px; }
.box { border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 12px; }
.calc { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 13px; }
.calc td { padding: 6px 4px; border-bottom: 1px solid #f0f0f0; }
.calc .num { text-align: right; white-space: nowrap; }
.calc .total td { font-weight: 600; border-bottom: none; }
:deep(.el-table .OVER) { background: #fff1f0; }
</style>
