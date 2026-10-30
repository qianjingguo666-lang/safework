<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane name="tasks">
        <template #label>巡检任务 <el-badge v-if="urgent" :value="urgent" class="badge" /></template>
      </el-tab-pane>
      <el-tab-pane label="巡检记录" name="history" />
    </el-tabs>

    <template v-if="tab === 'tasks'">
      <div class="summary">
        <el-radio-group v-model="filter" size="small">
          <el-radio-button value="todo">需要巡检（{{ urgent }}）</el-radio-button>
          <el-radio-button value="all">全部风险点（{{ tasks.length }}）</el-radio-button>
        </el-radio-group>
        <span class="muted">逾期 {{ count('OVERDUE') }} 个 · 今天到期 {{ count('TODAY') }} 个</span>
      </div>
      <el-empty v-if="!loading && !shown.length" :description="filter === 'todo' ? '今天没有需要巡检的风险点 👍' : '没有负责的风险点'" />
      <div v-loading="loading" class="tasks">
        <div v-for="t in shown" :key="t.pointId" class="task" :style="{ '--c': RISK_GRADES[t.riskLevel].color }">
          <div class="task-main">
            <div class="task-title">
              <RiskBadge :level="t.riskLevel" short />
              <span>{{ t.pointCode }} {{ t.pointName }}</span>
            </div>
            <div class="meta">
              <span>{{ t.location }}</span>
              <span>{{ t.measures.length }} 条管控措施</span>
              <span>每 {{ t.checkCycleDays }} 天巡检</span>
              <span>责任人 {{ t.responsibleName }}</span>
              <span v-if="t.lastCheckTime">上次 {{ t.lastCheckTime }}</span>
            </div>
          </div>
          <div class="task-side">
            <div class="due" :class="t.state.toLowerCase()">{{ dueText(t) }}</div>
            <el-button :type="t.state === 'UPCOMING' ? 'default' : 'primary'" @click="openCheck(t)">去巡检</el-button>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="toolbar">
        <el-select v-model="hq.result" placeholder="结果" clearable style="width: 140px" @change="loadHistory">
          <el-option label="措施有效" value="NORMAL" /><el-option label="有措施失效" value="ABNORMAL" />
        </el-select>
      </div>
      <el-table :data="history" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand">
              <div v-for="it in items(row)" :key="it.measure" :class="it.ok ? 'ok' : 'bad'">
                {{ it.ok ? '✓' : '✗' }} {{ it.measure }}<span v-if="it.note" class="muted">（{{ it.note }}）</span>
              </div>
              <div v-if="row.remark" class="muted">备注：{{ row.remark }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="checkTime" label="巡检时间" width="170" />
        <el-table-column label="风险点" min-width="200">
          <template #default="{ row }"><RiskBadge :level="row.riskLevel" short /> {{ row.pointCode }} {{ row.pointName }}</template>
        </el-table-column>
        <el-table-column prop="checkerName" label="巡检人" width="150" />
        <el-table-column label="结果" width="110">
          <template #default="{ row }"><el-tag :type="row.result === 'NORMAL' ? 'success' : 'danger'">{{ row.result === 'NORMAL' ? '措施有效' : '有措施失效' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="隐患" width="170">
          <template #default="{ row }">
            <el-link v-if="row.hazardNo" type="primary" @click="goHazard(row.hazardId)">{{ row.hazardNo }}</el-link>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="hq.current" :page-size="hq.size" :total="historyTotal" layout="total, prev, pager, next" @current-change="loadHistory" />
      </div>
    </template>

    <!-- 巡检：逐条确认管控措施 -->
    <el-dialog v-model="checkVisible" :title="current ? `巡检：${current.pointCode} ${current.pointName}` : ''" width="680px" top="6vh">
      <template v-if="current">
        <div class="check-head">
          <RiskBadge :level="current.riskLevel" />
          <span class="muted">{{ current.location }} · 请到现场逐条确认管控措施是否有效</span>
          <el-button size="small" link type="success" @click="allOk">全部有效</el-button>
        </div>
        <div v-for="(it, i) in checkItems" :key="i" class="check-item" :class="{ bad: it.ok === false }">
          <div class="ci-row">
            <span class="ci-no">{{ i + 1 }}</span>
            <span class="ci-text">{{ it.measure }}</span>
            <el-radio-group v-model="it.ok" size="small">
              <el-radio-button :value="true">有效</el-radio-button>
              <el-radio-button :value="false">失效</el-radio-button>
            </el-radio-group>
          </div>
          <el-input v-if="it.ok === false" v-model="it.note" size="small" maxlength="200" placeholder="说明问题，如：报警器过检定期" class="ci-note" />
        </div>
        <template v-if="failedCount">
          <el-alert type="error" :closable="false" show-icon class="fail-tip"
                    :title="`有 ${failedCount} 条措施失效。${current.riskLevel === 'MAJOR' || current.riskLevel === 'HIGH' ? '这是重大 / 较大风险点，上报后按重大隐患处理。' : ''}`" />
          <el-form label-width="96px" class="hz-form">
            <el-form-item label="上报隐患"><el-switch v-model="toHazard" active-text="同时上报隐患，进入整改闭环" /></el-form-item>
            <el-form-item v-if="toHazard" label="隐患标题"><el-input v-model="hazardTitle" maxlength="100" /></el-form-item>
          </el-form>
        </template>
        <el-input v-model="remark" type="textarea" :rows="2" maxlength="500"
                  :placeholder="failedCount && !toHazard ? '不上报隐患时必须说明现场是怎么处理的（必填）' : '备注（可选）'" />
      </template>
      <template #footer>
        <el-button @click="checkVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!allAnswered" @click="submit">提交巡检</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageRiskChecks, riskTasks, submitRiskCheck } from '@/api'
import { RISK_GRADES } from '@/utils/dict'
import RiskBadge from '../risk/RiskBadge.vue'

const router = useRouter()
const tab = ref('tasks')
const filter = ref('todo')
const tasks = ref([])
const loading = ref(false)
const count = (s) => tasks.value.filter((t) => t.state === s).length
const urgent = computed(() => count('OVERDUE') + count('TODAY'))
const shown = computed(() => (filter.value === 'todo' ? tasks.value.filter((t) => t.state !== 'UPCOMING') : tasks.value))
function dueText(t) {
  if (t.state === 'OVERDUE') return t.daysLeft < 0 ? `已逾期 ${-t.daysLeft} 天` : '已逾期'
  if (t.state === 'TODAY') return `今天 ${t.nextCheckTime.slice(11)} 前`
  return `${t.daysLeft} 天后`
}
async function load() {
  loading.value = true
  try {
    tasks.value = await riskTasks()
    if (!urgent.value) filter.value = 'all'
  } finally {
    loading.value = false
  }
}

// ---------- 巡检 ----------
const checkVisible = ref(false)
const current = ref(null)
const checkItems = ref([])
const toHazard = ref(true)
const hazardTitle = ref('')
const remark = ref('')
const submitting = ref(false)
const failedCount = computed(() => checkItems.value.filter((i) => i.ok === false).length)
const allAnswered = computed(() => checkItems.value.length && checkItems.value.every((i) => i.ok !== null))
watch(failedCount, () => {
  const first = checkItems.value.find((i) => i.ok === false)
  if (current.value && first) hazardTitle.value = `风险点【${current.value.pointName}】管控措施失效：${first.measure}`.slice(0, 100)
})
function openCheck(t) {
  current.value = t
  checkItems.value = t.measures.map((m) => ({ measure: m, ok: null, note: '' }))
  toHazard.value = true
  remark.value = ''
  checkVisible.value = true
}
function allOk() {
  checkItems.value.forEach((i) => { i.ok = true; i.note = '' })
}
async function submit() {
  if (failedCount.value && !toHazard.value && !remark.value.trim()) {
    ElMessage.warning('不上报隐患时，请在备注里说明现场是怎么处理的')
    return
  }
  submitting.value = true
  try {
    const r = await submitRiskCheck(current.value.pointId, {
      items: checkItems.value.map(({ measure, ok, note }) => ({ measure, ok, note })),
      remark: remark.value, toHazard: failedCount.value > 0 && toHazard.value, hazardTitle: hazardTitle.value
    })
    checkVisible.value = false
    if (r.hazardNo) {
      ElMessageBox.alert(`已上报隐患 ${r.hazardNo}，安全员会指派整改。下次巡检：${r.nextCheckTime}`, '巡检完成', {
        confirmButtonText: '查看隐患', type: 'warning', callback: (a) => { if (a === 'confirm') goHazard(r.hazardId) }
      })
    } else {
      ElMessage.success(`巡检完成，下次巡检：${r.nextCheckTime}`)
    }
    load()
  } finally {
    submitting.value = false
  }
}
function goHazard(id) {
  router.push({ path: '/safety/hazard', query: { detail: id } })
}

// ---------- 记录 ----------
const history = ref([])
const historyTotal = ref(0)
const hq = reactive({ current: 1, size: 10, result: '' })
async function loadHistory() {
  const data = await pageRiskChecks(hq)
  history.value = data.records
  historyTotal.value = data.total
}
function items(row) {
  try { return JSON.parse(row.items || '[]') } catch { return [] }
}
function onTab(name) {
  if (name === 'history') loadHistory()
}

onMounted(load)
</script>

<style scoped>
.badge { margin-left: 4px; }
.summary { display: flex; align-items: center; gap: 14px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.tasks { display: flex; flex-direction: column; gap: 10px; min-height: 80px; }
.task { display: flex; gap: 16px; align-items: center; border: 1px solid #ebeef5; border-left: 4px solid var(--c); border-radius: 8px; padding: 12px 16px; }
.task-main { flex: 1; min-width: 0; }
.task-title { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.meta { display: flex; flex-wrap: wrap; gap: 4px 16px; color: #909399; font-size: 12px; margin-top: 6px; }
.task-side { display: flex; align-items: center; gap: 14px; }
.due { font-size: 13px; color: #909399; min-width: 90px; text-align: right; }
.due.overdue { color: #f56c6c; font-weight: 600; }
.due.today { color: #e6a23c; font-weight: 600; }
.expand { padding: 4px 48px; line-height: 1.9; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.check-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.check-head .el-button { margin-left: auto; }
.check-item { border: 1px solid #ebeef5; border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; }
.check-item.bad { border-color: #fbc4c4; background: #fef6f6; }
.ci-row { display: flex; align-items: center; gap: 10px; }
.ci-no { width: 22px; height: 22px; border-radius: 50%; background: #f0f2f5; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.ci-text { flex: 1; line-height: 1.5; }
.ci-note { margin: 8px 0 0 32px; width: calc(100% - 32px); }
.fail-tip { margin: 10px 0; }
.hz-form { margin-bottom: 4px; }
@media (max-width: 768px) {
  .task { flex-direction: column; align-items: stretch; }
  .task-side { justify-content: space-between; }
  .ci-row { flex-wrap: wrap; }
}
</style>
