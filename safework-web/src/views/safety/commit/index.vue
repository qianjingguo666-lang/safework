<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="今日研判" name="today">
        <div v-loading="loading">
          <template v-if="t">
            <div class="head">
              <b>{{ t.date }}</b>
              <el-tag v-if="committed" type="success" effect="dark">已承诺公告</el-tag>
              <el-tag v-else-if="!t.missing.length" type="warning" effect="dark">研判完成，待主要负责人承诺</el-tag>
              <el-tag v-else type="info">研判中，还差 {{ t.missing.length }} 个方面</el-tag>
              <el-tag v-if="t.judgeLate" type="danger">研判已超过 9:00</el-tag>
              <el-tag v-if="t.commitLate" type="danger">承诺已超过 10:00</el-tag>
              <div class="spacer" />
              <span class="muted">生产、设备、工艺、安全负责人 9:00 前研判，主要负责人 10:00 前签字承诺</span>
            </div>

            <div class="aspects">
              <div v-for="a in t.aspects" :key="a.aspect" class="aspect" :class="{ done: a.judgerName, bad: a.abnormal }">
                <div class="a-head">
                  <b>{{ a.label }}</b>
                  <el-tag v-if="a.judgerName" size="small" :type="a.abnormal ? 'danger' : 'success'">{{ a.abnormal ? `${a.abnormal} 项异常` : '正常' }}</el-tag>
                  <el-tag v-else size="small" type="info">未研判</el-tag>
                </div>
                <div class="muted">{{ a.judgerName ? `${a.judgerName} ${a.judgeTime.slice(11, 16)}` : '　' }}</div>
                <div v-for="i in a.items" :key="i.code" class="it">
                  <el-icon v-if="i.ok === true" class="ok"><CircleCheck /></el-icon>
                  <el-icon v-else-if="i.ok === false" class="no"><WarningFilled /></el-icon>
                  <el-icon v-else class="none"><Remove /></el-icon>
                  <span>{{ i.text }}<template v-if="i.ok === false"><br /><span class="abn">{{ i.note }}；措施：{{ i.measure }}</span></template></span>
                </div>
                <el-button v-if="t.canJudge" size="small" :type="a.judgerName ? 'default' : 'primary'" class="a-btn" @click="openJudge(a)">
                  {{ a.judgerName ? '重新研判' : '研判' }}
                </el-button>
              </div>
            </div>

            <div class="sec">系统统计<span class="muted">（{{ committed ? '承诺时' : '现在' }}的数据，承诺时会固定到公告里）</span></div>
            <div class="stats">
              <div v-for="[k, label] in STAT_LABELS" :key="k" class="stat"><b>{{ t.stats[k] }}</b><span>{{ label }}</span></div>
            </div>

            <div v-if="t.canSign" class="sign-box">
              <div class="sec">主要负责人承诺</div>
              <div class="promise">{{ promiseText }}</div>
              <SignaturePad ref="padRef" class="pad" placeholder="主要负责人在此手写签名" />
              <div class="sign-tools">
                <el-button @click="padRef.clear()">重写</el-button>
                <el-button type="primary" :loading="signing" @click="sign">签字承诺并公告</el-button>
              </div>
            </div>
            <el-alert v-else-if="!committed && !t.missing.length" type="info" :closable="false" show-icon title="研判已完成，等主要负责人（企业管理员）签字承诺" />
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="承诺公告" name="board">
        <div class="toolbar">
          <el-date-picker v-model="boardDate" type="date" value-format="YYYY-MM-DD" placeholder="最近一次" clearable style="width: 160px" @change="loadBoard" />
          <div class="spacer" />
          <el-button icon="Printer" @click="print">打印</el-button>
        </div>
        <div v-loading="bLoading" id="commit-board">
          <BoardCard v-if="board" :board="board" />
          <el-empty v-else-if="!bLoading" :description="boardErr || '还没有承诺公告'" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="历史记录" name="history">
        <div class="toolbar">
          <el-date-picker v-model="month" type="month" value-format="YYYY-MM" :clearable="false" style="width: 140px" @change="loadHistory" />
          <span v-if="h" class="muted">按时承诺 {{ h.onTimeDays }} / {{ h.days }} 天，按时率 <b :class="{ danger: h.rate != null && h.rate < 100 }">{{ h.rate ?? '-' }}%</b></span>
        </div>
        <el-table v-loading="hLoading" :data="h?.rows || []" stripe class="clickable" @row-click="openDay">
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="HISTORY_STATUS[row.status].type">{{ HISTORY_STATUS[row.status].label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="承诺时间" width="110">
            <template #default="{ row }"><span :class="{ danger: row.commitTime && !row.onTime }">{{ row.commitTime?.slice(11, 16) || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="按时" width="80"><template #default="{ row }">{{ row.status === 'COMMITTED' ? (row.onTime ? '是' : '超时') : '-' }}</template></el-table-column>
          <el-table-column label="异常" width="80"><template #default="{ row }"><span :class="{ danger: row.abnormal }">{{ row.abnormal ?? '-' }}</span></template></el-table-column>
          <el-table-column prop="committerName" label="承诺人" min-width="140" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <JudgeForm v-model="jVisible" :aspect="jAspect?.aspect" :view="jAspect" :commitment="t?.commitment" :last-units="t?.lastUnits" @saved="loadToday" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commitBoard, commitHistory, commitSign, commitToday } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'
import BoardCard from './BoardCard.vue'
import JudgeForm from './JudgeForm.vue'
import { HISTORY_STATUS, STAT_LABELS } from './commit'

const tab = ref('today')
const t = ref(null)
const loading = ref(false)
const committed = computed(() => t.value?.commitment?.status === 'COMMITTED')
const abnormalCount = computed(() => (t.value?.aspects || []).reduce((s, a) => s + a.abnormal, 0))
const promiseText = computed(() => abnormalCount.value
  ? `今天我公司已进行安全风险研判，发现 ${abnormalCount.value} 项异常情况，已逐项落实管控措施，我承诺在管控措施落实到位的前提下组织生产，罐区、仓库等重大危险源安全风险得到有效管控。`
  : '今天我公司已进行安全风险研判，各项安全风险防控措施已落实到位，我承诺所有生产装置处于安全运行状态，罐区、仓库等重大危险源安全风险得到有效管控。')

async function loadToday() {
  loading.value = true
  try {
    t.value = await commitToday()
  } finally {
    loading.value = false
  }
}

const jVisible = ref(false)
const jAspect = ref(null)
function openJudge(a) {
  jAspect.value = a
  jVisible.value = true
}

const padRef = ref()
const signing = ref(false)
async function sign() {
  if (padRef.value.isEmpty()) return ElMessage.warning('请先手写签名')
  await ElMessageBox.confirm('签字后今天的研判就不能再改了，确认承诺并公告？', '承诺公告')
  signing.value = true
  try {
    await commitSign(padRef.value.toDataURL())
    ElMessage.success('已承诺公告')
    await loadToday()
    tab.value = 'board'
    boardDate.value = null
    loadBoard()
  } finally {
    signing.value = false
  }
}

const board = ref(null)
const boardDate = ref(null)
const boardErr = ref('')
const bLoading = ref(false)
async function loadBoard() {
  bLoading.value = true
  board.value = null
  boardErr.value = ''
  try {
    board.value = await commitBoard(boardDate.value || undefined)
  } catch (e) {
    boardErr.value = e?.message || ''
  } finally {
    bLoading.value = false
  }
}
function print() {
  window.print()
}

const d0 = new Date()
const month = ref(`${d0.getFullYear()}-${String(d0.getMonth() + 1).padStart(2, '0')}`)
const h = ref(null)
const hLoading = ref(false)
async function loadHistory() {
  hLoading.value = true
  try {
    h.value = await commitHistory(month.value)
  } finally {
    hLoading.value = false
  }
}
function openDay(row) {
  if (row.status !== 'COMMITTED') return
  boardDate.value = row.date
  tab.value = 'board'
  loadBoard()
}

function onTab(name) {
  if (name === 'today') loadToday()
  if (name === 'board' && !board.value) loadBoard()
  if (name === 'history') loadHistory()
}
onMounted(loadToday)
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.head b { font-size: 16px; }
.muted { color: #999; font-size: 12px; font-weight: normal; }
.danger { color: #f56c6c; }
.aspects { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.aspect { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
.aspect.done { border-color: #c2e7b0; }
.aspect.bad { border-color: #f5c2c2; }
.a-head { display: flex; align-items: center; justify-content: space-between; }
.it { display: flex; gap: 6px; font-size: 13px; line-height: 1.5; }
.it .el-icon { margin-top: 3px; flex: none; }
.ok { color: #52c41a; }
.no { color: #f56c6c; }
.none { color: #ccc; }
.abn { color: #c0392b; font-size: 12px; }
.a-btn { margin-top: auto; align-self: flex-start; }
.sec { font-weight: 600; margin: 18px 0 8px; }
.stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
.stat { border: 1px solid #ebeef5; border-radius: 6px; padding: 8px 12px; display: flex; align-items: baseline; gap: 6px; }
.stat b { font-size: 20px; }
.stat span { color: #888; font-size: 12px; }
.sign-box { margin-top: 8px; }
.promise { line-height: 1.9; text-indent: 2em; padding: 10px 12px; background: #fffdf8; border: 1px solid #f0e2d0; border-radius: 6px; margin-bottom: 10px; }
.pad { max-width: 520px; }
.sign-tools { margin-top: 8px; display: flex; gap: 8px; }
.clickable :deep(tr) { cursor: pointer; }
@media print {
  .toolbar { display: none; }
}
</style>
