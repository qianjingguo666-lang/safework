<template>
  <div>
    <div v-loading="loading" class="exam-list">
      <el-empty v-if="!loading && !exams.length" description="暂时没有需要参加的考试" />
      <div v-for="e in exams" :key="e.examId" class="page-card exam" :class="e.state.toLowerCase()">
        <div class="exam-main">
          <div class="exam-title">
            {{ e.title }}
            <el-tag :type="STATES[e.state].type" size="small" effect="dark">{{ STATES[e.state].label }}</el-tag>
          </div>
          <div v-if="e.description" class="desc">{{ e.description }}</div>
          <div class="meta">
            <span><el-icon><Document /></el-icon>{{ e.questionCount }} 题 · 满分 {{ e.totalScore }} · {{ e.passScore }} 分及格</span>
            <span><el-icon><Timer /></el-icon>{{ e.durationMinutes }} 分钟</span>
            <span><el-icon><Calendar /></el-icon>{{ e.startTime }} ~ {{ e.endTime }}</span>
            <span><el-icon><RefreshRight /></el-icon>已考 {{ e.attempts }} / {{ e.maxAttempts }} 次</span>
          </div>
        </div>
        <div class="exam-side">
          <div v-if="e.bestScore != null" class="best" :class="e.passed ? 'ok' : 'bad'">
            <b>{{ e.bestScore }}</b><span>最高分</span>
          </div>
          <div class="btns">
            <el-button v-if="e.inProgressRecordId" type="warning" @click="resume(e)">继续考试</el-button>
            <el-button v-else-if="e.canStart" type="primary" @click="begin(e)">{{ e.attempts ? '再考一次' : '开始考试' }}</el-button>
            <el-button v-if="e.bestRecordId" @click="viewResult(e.bestRecordId)">查看成绩</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 答题 -->
    <el-dialog v-model="taking" fullscreen :show-close="false" :close-on-press-escape="false" class="take-dialog" @closed="paper = null">
      <template #header>
        <div v-if="paper" class="take-head">
          <div class="take-title">{{ paper.examTitle }} <span class="muted">第 {{ paper.attemptNo }} 次</span></div>
          <div class="clock" :class="{ urgent: remaining < 300 }"><el-icon><Timer /></el-icon>{{ clock }}</div>
          <div class="progress">已答 {{ answeredCount }} / {{ paper.items.length }}</div>
          <div class="save-state" :class="saveState.type">{{ saveState.text }}</div>
          <el-button @click="leave">稍后继续</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmSubmit">交卷</el-button>
        </div>
      </template>
      <div v-if="paper" class="take-body">
        <div class="questions">
          <div v-for="q in paper.items" :id="`q-${q.index}`" :key="q.index" class="question">
            <div class="q-title">
              <span class="no">{{ q.index }}.</span>
              <el-tag size="small" effect="plain" :type="QUESTION_TYPES[q.type]?.type">{{ QUESTION_TYPES[q.type]?.label }}</el-tag>
              <span>{{ q.content }}</span>
              <span class="pts">（{{ q.score }} 分）</span>
            </div>
            <el-checkbox-group v-if="q.type === 'MULTI'" v-model="multi[q.index]" class="choices" @change="changed">
              <el-checkbox v-for="(o, i) in q.options" :key="i" :value="letter(i)" border>{{ letter(i) }}. {{ o }}</el-checkbox>
            </el-checkbox-group>
            <el-radio-group v-else v-model="single[q.index]" class="choices" @change="changed">
              <el-radio v-for="(o, i) in q.options" :key="i" :value="letter(i)" border>{{ q.type === 'JUDGE' ? o : `${letter(i)}. ${o}` }}</el-radio>
            </el-radio-group>
          </div>
        </div>
        <div class="nav">
          <div class="nav-title">答题卡</div>
          <div class="nav-grid">
            <a v-for="q in paper.items" :key="q.index" :class="{ done: !!answerOf(q) }" @click="jump(q.index)">{{ q.index }}</a>
          </div>
          <div class="legend"><i class="done" />已答 <i />未答</div>
          <div class="muted tip">答案会自动保存，刷新页面或换电脑都可以继续答。时间到会自动交卷。</div>
        </div>
      </div>
    </el-dialog>

    <!-- 成绩 -->
    <el-dialog v-model="resultVisible" title="考试成绩" width="760px" top="5vh">
      <ExamReview v-if="result" :result="result" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examPaper, examResult, myExams, saveExamAnswers, startExam, submitExam } from '@/api'
import { QUESTION_TYPES, letter } from '@/utils/dict'
import ExamReview from './ExamReview.vue'

const STATES = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  TODO: { label: '待参加', type: 'primary' },
  IN_PROGRESS: { label: '答题中', type: 'warning' },
  PASSED: { label: '已通过', type: 'success' },
  RETRY: { label: '未通过，可补考', type: 'danger' },
  FAILED: { label: '未通过', type: 'danger' },
  MISSED: { label: '已结束', type: 'info' }
}
const exams = ref([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    exams.value = await myExams()
  } finally {
    loading.value = false
  }
}

// ---------- 答题 ----------
const taking = ref(false)
const paper = ref(null)
const single = reactive({})
const multi = reactive({})
const submitting = ref(false)
const saveState = reactive({ type: '', text: '' })
const now = ref(Date.now())
let offset = 0
let timer = null
let saveTimer = null
let autoSubmitted = false

const remaining = computed(() => {
  if (!paper.value) return 0
  const deadline = new Date(paper.value.deadline.replace(' ', 'T')).getTime()
  return Math.max(0, Math.floor((deadline - (now.value + offset)) / 1000))
})
const clock = computed(() => {
  const s = remaining.value
  const h = Math.floor(s / 3600)
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
})
function answerOf(q) {
  return q.type === 'MULTI' ? [...(multi[q.index] || [])].sort().join('') : single[q.index] || ''
}
function collect() {
  const result = {}
  paper.value.items.forEach((q) => {
    const a = answerOf(q)
    if (a) result[q.index] = a
  })
  return result
}
const answeredCount = computed(() => (paper.value ? paper.value.items.filter((q) => answerOf(q)).length : 0))
const backupKey = () => `safework_exam_${paper.value.recordId}`

function open(p) {
  paper.value = p
  // 用服务器时间校正本机时钟，倒计时以服务器为准
  offset = new Date(p.serverNow.replace(' ', 'T')).getTime() - Date.now()
  Object.keys(single).forEach((k) => delete single[k])
  Object.keys(multi).forEach((k) => delete multi[k])
  let saved = { ...(p.answers || {}) }
  // 服务器保存失败时答案暂存在本机，这里合并回来
  try {
    const local = JSON.parse(localStorage.getItem(backupKey()) || 'null')
    if (local) saved = { ...saved, ...local }
  } catch { /* 本机存储不可用 */ }
  p.items.forEach((q) => {
    const a = saved[q.index] || ''
    if (q.type === 'MULTI') multi[q.index] = a ? a.split('') : []
    else single[q.index] = a || undefined
  })
  autoSubmitted = false
  Object.assign(saveState, { type: '', text: '答案自动保存' })
  taking.value = true
  clearInterval(timer)
  timer = setInterval(tick, 1000)
}
function tick() {
  now.value = Date.now()
  if (taking.value && paper.value && remaining.value <= 0 && !autoSubmitted) {
    autoSubmitted = true
    ElMessage.warning('考试时间到，正在自动交卷')
    submit()
  }
}
function changed() {
  try { localStorage.setItem(backupKey(), JSON.stringify(collect())) } catch { /* 忽略 */ }
  Object.assign(saveState, { type: 'saving', text: '保存中…' })
  clearTimeout(saveTimer)
  saveTimer = setTimeout(save, 1200)
}
async function save() {
  if (!paper.value) return
  try {
    await saveExamAnswers(paper.value.recordId, collect())
    const t = new Date()
    Object.assign(saveState, { type: 'ok', text: `已保存 ${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}:${String(t.getSeconds()).padStart(2, '0')}` })
  } catch {
    Object.assign(saveState, { type: 'err', text: '保存失败，答案已暂存在本机' })
  }
}
function jump(i) {
  document.getElementById(`q-${i}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
async function begin(e) {
  const tip = `共 ${e.questionCount} 题，限时 ${e.durationMinutes} 分钟，开始后开始计时，中途离开时间不会暂停。` +
    `这是第 ${e.attempts + 1} 次（最多 ${e.maxAttempts} 次）。`
  await ElMessageBox.confirm(tip, `开始考试：${e.title}`, { confirmButtonText: '开始', type: 'info' })
  open(await startExam(e.examId))
}
async function resume(e) {
  try {
    open(await examPaper(e.inProgressRecordId))
  } catch (err) {
    // 409：时间已到被自动交卷，刷新列表即可看到成绩
    if (err.code === 409) load()
  }
}
async function confirmSubmit() {
  const left = paper.value.items.length - answeredCount.value
  await ElMessageBox.confirm(left ? `还有 ${left} 道题没答，交卷后不能修改。确定交卷？` : '交卷后不能修改，确定交卷？', '交卷',
    { type: left ? 'warning' : 'info', confirmButtonText: '交卷' })
  submit()
}
async function submit() {
  clearTimeout(saveTimer)
  submitting.value = true
  try {
    result.value = await submitExam(paper.value.recordId, collect())
    try { localStorage.removeItem(backupKey()) } catch { /* 忽略 */ }
    taking.value = false
    clearInterval(timer)
    resultVisible.value = true
    load()
  } finally {
    submitting.value = false
  }
}
async function leave() {
  clearTimeout(saveTimer)
  await save()
  await ElMessageBox.confirm('答案已保存，可以稍后点【继续考试】接着答。注意：离开期间考试时间不会暂停。', '稍后继续',
    { confirmButtonText: '离开', cancelButtonText: '继续答题', type: 'warning' })
  taking.value = false
  clearInterval(timer)
  load()
}

// ---------- 成绩 ----------
const resultVisible = ref(false)
const result = ref(null)
async function viewResult(recordId) {
  result.value = await examResult(recordId)
  resultVisible.value = true
}

onMounted(load)
onBeforeUnmount(() => {
  clearInterval(timer)
  clearTimeout(saveTimer)
})
</script>

<style scoped>
.exam-list { display: flex; flex-direction: column; gap: 12px; min-height: 200px; }
.exam { display: flex; gap: 20px; align-items: center; border-left: 4px solid #1f6feb; }
.exam.passed { border-left-color: #67c23a; }
.exam.retry, .exam.failed { border-left-color: #f56c6c; }
.exam.missed, .exam.not_started { border-left-color: #c0c4cc; }
.exam.in_progress { border-left-color: #e6a23c; }
.exam-main { flex: 1; min-width: 0; }
.exam-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.desc { color: #606266; margin-top: 6px; font-size: 13px; }
.meta { display: flex; flex-wrap: wrap; gap: 6px 18px; margin-top: 10px; color: #909399; font-size: 13px; }
.meta span { display: inline-flex; align-items: center; gap: 4px; }
.exam-side { display: flex; align-items: center; gap: 18px; }
.best { text-align: center; }
.best b { display: block; font-size: 28px; }
.best span { font-size: 12px; color: #909399; }
.best.ok b { color: #67c23a; }
.best.bad b { color: #f56c6c; }
.btns { display: flex; flex-direction: column; gap: 8px; }
.btns .el-button + .el-button { margin-left: 0; }
.take-head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.take-title { font-size: 17px; font-weight: 600; flex: 1; min-width: 180px; }
.muted { color: #909399; font-size: 12px; font-weight: normal; }
.clock { display: flex; align-items: center; gap: 4px; font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; color: #1f6feb; }
.clock.urgent { color: #f56c6c; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.5; } }
.progress { color: #606266; }
.save-state { font-size: 12px; color: #909399; min-width: 110px; }
.save-state.ok { color: #67c23a; }
.save-state.err { color: #f56c6c; }
.take-body { display: flex; gap: 20px; max-width: 1100px; margin: 0 auto; }
.questions { flex: 1; min-width: 0; }
.question { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 14px 18px; margin-bottom: 12px; }
.q-title { display: flex; gap: 6px; align-items: flex-start; line-height: 1.7; margin-bottom: 10px; }
.q-title .no { font-weight: 600; }
.pts { color: #909399; font-size: 12px; white-space: nowrap; }
.choices { display: flex; flex-direction: column; align-items: stretch; gap: 8px; }
.choices :deep(.el-radio), .choices :deep(.el-checkbox) { margin-right: 0; height: auto; min-height: 36px; padding: 8px 12px; white-space: normal; }
.choices :deep(.el-radio__label), .choices :deep(.el-checkbox__label) { white-space: normal; line-height: 1.5; }
.nav { width: 220px; flex-shrink: 0; position: sticky; top: 76px; align-self: flex-start; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; }
.nav-title { font-weight: 600; margin-bottom: 10px; }
.nav-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.nav-grid a { display: flex; align-items: center; justify-content: center; height: 30px; border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; font-size: 13px; }
.nav-grid a.done { background: #1f6feb; border-color: #1f6feb; color: #fff; }
.legend { display: flex; align-items: center; gap: 6px; margin-top: 12px; font-size: 12px; color: #909399; }
.legend i { display: inline-block; width: 12px; height: 12px; border: 1px solid #dcdfe6; border-radius: 2px; margin-left: 6px; }
.legend i.done { background: #1f6feb; border-color: #1f6feb; }
.tip { margin-top: 10px; line-height: 1.6; }
@media (max-width: 768px) {
  .exam { flex-direction: column; align-items: stretch; }
  .exam-side { justify-content: space-between; }
  .btns { flex-direction: row; }
  .nav { display: none; }
  .save-state { display: none; }
}
</style>
<style>
.take-dialog { background: #f4f6f9; }
.take-dialog .el-dialog__header { background: #fff; margin: -16px -16px 16px; padding: 12px 20px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); position: sticky; top: 0; z-index: 2; }
</style>
