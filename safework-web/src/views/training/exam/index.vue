<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="考试名称" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in EXAM_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-perm="'training:exam:edit'" type="primary" icon="Plus" @click="openForm()">新建考试</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="title" label="考试名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="题目" width="140">
        <template #default="{ row }">
          <div class="small">单选 {{ row.singleCount }} · 多选 {{ row.multiCount }} · 判断 {{ row.judgeCount }}</div>
          <div class="muted">{{ row.category || '全部分类' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="满分 / 及格" width="100"><template #default="{ row }">{{ row.totalScore }} / {{ row.passScore }}</template></el-table-column>
      <el-table-column label="时长" width="70"><template #default="{ row }">{{ row.durationMinutes }} 分钟</template></el-table-column>
      <el-table-column label="考试时间" width="170">
        <template #default="{ row }"><div class="small">{{ fmt(row.startTime) }}</div><div class="small">至 {{ fmt(row.endTime) }}</div></template>
      </el-table-column>
      <el-table-column label="参考范围" width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deptNames || '全体员工' }}</template>
      </el-table-column>
      <el-table-column label="交卷 / 通过" width="110">
        <template #default="{ row }">
          <span v-if="row.status !== 'DRAFT'">{{ row.submittedUsers }} 人 / <b class="ok">{{ row.passedUsers }}</b> 人</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="EXAM_STATUS[row.status].type">{{ EXAM_STATUS[row.status].label }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'DRAFT' && canEdit">
            <el-button link type="primary" @click="openForm(row)">修改</el-button>
            <el-button link type="success" @click="publish(row)">发布</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
          <template v-else-if="row.status !== 'DRAFT'">
            <el-button link type="primary" @click="openStats(row)">成绩</el-button>
            <el-button v-if="row.status === 'PUBLISHED' && canEdit" link type="warning" @click="close(row)">结束</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <!-- 新建 / 修改 -->
    <el-dialog v-model="formVisible" :title="editId ? '修改考试' : '新建考试'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="考试名称" prop="title"><el-input v-model="form.title" maxlength="100" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="抽题分类">
          <el-select v-model="form.category" clearable filterable placeholder="全部分类" style="width: 220px" @change="loadCounts">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="muted hint">每人从题库中随机抽题，选项顺序也随机</span>
        </el-form-item>
        <el-form-item label="组卷规则" required>
          <table class="rule">
            <tr><th>题型</th><th>题数</th><th>每题分值</th><th>题库可用</th></tr>
            <tr v-for="r in RULES" :key="r.type">
              <td>{{ r.label }}</td>
              <td><el-input-number v-model="form[r.count]" :min="0" :max="50" size="small" /></td>
              <td><el-input-number v-model="form[r.score]" :min="1" :max="20" size="small" /></td>
              <td :class="{ lack: form[r.count] > (counts[r.type] ?? 0) }">
                {{ counts[r.type] ?? '-' }} 道<span v-if="form[r.count] > (counts[r.type] ?? 0)">（不够）</span>
              </td>
            </tr>
          </table>
          <div class="total">满分 <b>{{ totalScore }}</b> 分</div>
        </el-form-item>
        <el-row>
          <el-col :span="8"><el-form-item label="及格分" prop="passScore"><el-input-number v-model="form.passScore" :min="1" :max="Math.max(1, totalScore)" style="width: 110px" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="时长(分钟)" prop="durationMinutes"><el-input-number v-model="form.durationMinutes" :min="5" :max="240" style="width: 110px" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="可考次数" prop="maxAttempts"><el-input-number v-model="form.maxAttempts" :min="1" :max="10" style="width: 110px" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="考试时间" prop="range">
          <el-date-picker v-model="form.range" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm"
                          start-placeholder="开始" end-placeholder="结束" style="width: 100%" />
        </el-form-item>
        <el-form-item label="参考部门">
          <el-tree-select v-model="form.deptIds" :data="depts" :props="{ label: 'deptName', children: 'children' }" node-key="id"
                          multiple check-strictly clearable placeholder="不选表示全体员工（选上级部门包含下级）" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交卷后">
          <el-switch v-model="form.showAnswer" active-text="显示正确答案和解析" inactive-text="只显示分数" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 成绩统计 -->
    <el-drawer v-model="statsVisible" :title="stats?.exam.title" size="760px">
      <template v-if="stats">
        <div class="kpis">
          <div class="kpi"><div class="label">应参加</div><div class="value">{{ stats.targetUsers }}</div></div>
          <div class="kpi"><div class="label">已交卷</div><div class="value">{{ stats.submittedUsers }}</div></div>
          <div class="kpi"><div class="label">通过率</div><div class="value">{{ stats.passRate ?? '-' }}<small v-if="stats.passRate != null">%</small></div></div>
          <div class="kpi"><div class="label">平均分</div><div class="value">{{ stats.avgScore ?? '-' }}</div></div>
          <div class="kpi"><div class="label">最高 / 最低</div><div class="value">{{ stats.maxScore ?? '-' }} / {{ stats.minScore ?? '-' }}</div></div>
        </div>

        <div class="section-title">分数分布（得分率）</div>
        <div class="buckets">
          <div v-for="b in stats.buckets" :key="b.name" class="bucket">
            <span class="b-name">{{ b.name }}</span>
            <div class="b-bar"><div class="b-fill" :style="{ width: barWidth(b.count) }" :class="{ low: b.name.includes('以下') }" /></div>
            <span class="b-count">{{ b.count }} 人</span>
          </div>
        </div>

        <template v-if="stats.hardest.length">
          <div class="section-title">正确率最低的题（培训时重点讲解）</div>
          <div v-for="q in stats.hardest" :key="q.questionId" class="hard">
            <el-tag size="small" :type="QUESTION_TYPES[q.type]?.type" effect="plain">{{ QUESTION_TYPES[q.type]?.label }}</el-tag>
            <span class="hard-content">{{ q.content }}</span>
            <span class="hard-rate" :class="{ low: q.rate < 60 }">{{ q.rate }}%</span>
            <span class="muted">（{{ q.correct }}/{{ q.answered }}）</span>
          </div>
        </template>

        <template v-if="stats.notTaken.length">
          <div class="section-title">还没有交卷（{{ stats.targetUsers - stats.submittedUsers }} 人）</div>
          <div class="names"><el-tag v-for="n in stats.notTaken" :key="n" type="info" effect="plain">{{ n }}</el-tag></div>
        </template>

        <div class="section-title records-title">
          答卷
          <el-button size="small" icon="Download" :loading="exporting" @click="doExport">导出成绩</el-button>
        </div>
        <el-table :data="records" size="small" stripe>
          <el-table-column prop="userName" label="姓名" min-width="140" />
          <el-table-column prop="deptName" label="部门" width="110" />
          <el-table-column label="第几次" width="70"><template #default="{ row }">{{ row.attemptNo }}</template></el-table-column>
          <el-table-column label="得分" width="80">
            <template #default="{ row }"><b :class="row.passed ? 'ok' : 'bad'">{{ row.score }}</b></template>
          </el-table-column>
          <el-table-column prop="submitTime" label="交卷时间" width="160" />
          <el-table-column label="" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.autoSubmit" size="small" type="warning">超时自动</el-tag>
              <el-button link type="primary" @click="viewRecord(row)">答卷</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="recordPage" :page-size="10" :total="recordTotal" layout="total, prev, pager, next" size="small" @current-change="loadRecords" />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="recordVisible" :title="record ? `${record.userName} 的答卷：${record.score} 分` : ''" width="720px" top="5vh">
      <ExamReview v-if="record" :result="record" />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  closeExam, createExam, deptTree, examQuestionCount, examRecordDetail, examRecords, examStats, getExam, pageExams,
  publishExam, questionCategories, removeExam, updateExam
} from '@/api'
import { EXAM_STATUS, QUESTION_TYPES } from '@/utils/dict'
import { downloadFile } from '@/utils/download'
import ExamReview from '../my/ExamReview.vue'

const RULES = [
  { type: 'SINGLE', label: '单选题', count: 'singleCount', score: 'singleScore' },
  { type: 'MULTI', label: '多选题', count: 'multiCount', score: 'multiScore' },
  { type: 'JUDGE', label: '判断题', count: 'judgeCount', score: 'judgeScore' }
]
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('training:exam:edit'))
const query = reactive({ current: 1, size: 10, keyword: '', status: '' })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const fmt = (t) => (t ? t.slice(0, 16) : '')

async function load() {
  loading.value = true
  try {
    const data = await pageExams(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}

// ---------- 新建 / 修改 ----------
const formVisible = ref(false)
const formRef = ref()
const editId = ref(null)
const saving = ref(false)
const categories = ref([])
const depts = ref([])
const counts = ref({})
const form = reactive({})
const totalScore = computed(() => RULES.reduce((s, r) => s + (form[r.count] || 0) * (form[r.score] || 0), 0))
const rules = {
  title: [{ required: true, message: '请填写考试名称', trigger: 'blur' }],
  range: [{ required: true, message: '请选择考试时间', trigger: 'change' }]
}
function pad(n) { return String(n).padStart(2, '0') }
function toStr(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00` }
async function openForm(row) {
  editId.value = row?.id || null
  // 默认：下一个整点开始，持续 7 天
  const now = new Date()
  now.setMinutes(0, 0, 0)
  now.setHours(now.getHours() + 1)
  const week = new Date(now)
  week.setDate(week.getDate() + 7)
  if (row) {
    const e = await getExam(row.id)
    Object.assign(form, { ...e, range: [e.startTime, e.endTime], showAnswer: e.showAnswer === 1,
      deptIds: e.deptIds ? e.deptIds.split(',') : [] })
  } else {
    Object.assign(form, { title: '', description: '', category: '', singleCount: 10, singleScore: 4, multiCount: 5, multiScore: 6,
      judgeCount: 10, judgeScore: 3, passScore: 60, durationMinutes: 30, maxAttempts: 2, range: [toStr(now), toStr(week)],
      deptIds: [], showAnswer: true })
  }
  formVisible.value = true
  const [c, d] = await Promise.all([questionCategories(), deptTree()])
  categories.value = c
  depts.value = d
  loadCounts()
}
async function loadCounts() {
  counts.value = await examQuestionCount(form.category || undefined)
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const data = {
      title: form.title, description: form.description, category: form.category || null,
      singleCount: form.singleCount, singleScore: form.singleScore, multiCount: form.multiCount, multiScore: form.multiScore,
      judgeCount: form.judgeCount, judgeScore: form.judgeScore, passScore: form.passScore, durationMinutes: form.durationMinutes,
      maxAttempts: form.maxAttempts, startTime: form.range[0], endTime: form.range[1], deptIds: form.deptIds, showAnswer: form.showAnswer
    }
    if (editId.value) await updateExam(editId.value, data)
    else await createExam(data)
    ElMessage.success('已保存为草稿，确认无误后点【发布】')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function publish(row) {
  await ElMessageBox.confirm(`发布后会通知参考人员，考试规则不能再修改。确定发布【${row.title}】？`, '发布考试', { type: 'warning' })
  const n = await publishExam(row.id)
  ElMessage.success(`已发布，已通知 ${n} 人`)
  load()
}
async function close(row) {
  await ElMessageBox.confirm('结束后不能再参加考试，正在答题的人会按已保存的答案自动交卷。确定结束？', '结束考试', { type: 'warning' })
  const n = await closeExam(row.id)
  ElMessage.success(n ? `已结束，${n} 份答卷自动交卷` : '已结束')
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`确定删除草稿【${row.title}】？`, '删除', { type: 'warning' })
  await removeExam(row.id)
  ElMessage.success('已删除')
  load()
}

// ---------- 成绩 ----------
const statsVisible = ref(false)
const stats = ref(null)
const records = ref([])
const recordTotal = ref(0)
const recordPage = ref(1)
const exporting = ref(false)
const recordVisible = ref(false)
const record = ref(null)
const maxBucket = computed(() => Math.max(1, ...(stats.value?.buckets || []).map((b) => b.count)))
const barWidth = (n) => `${Math.round((n / maxBucket.value) * 100)}%`
async function openStats(row) {
  stats.value = null
  statsVisible.value = true
  recordPage.value = 1
  stats.value = await examStats(row.id)
  loadRecords()
}
async function loadRecords() {
  const data = await examRecords(stats.value.exam.id, { current: recordPage.value, size: 10 })
  records.value = data.records
  recordTotal.value = data.total
}
async function viewRecord(row) {
  record.value = await examRecordDetail(row.id)
  recordVisible.value = true
}
async function doExport() {
  exporting.value = true
  try {
    await downloadFile(`/training/exam/${stats.value.exam.id}/export`, {}, `${stats.value.exam.title}_成绩.xlsx`)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.small { font-size: 13px; }
.muted { color: #909399; font-size: 12px; }
.hint { margin-left: 10px; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.rule { border-collapse: collapse; }
.rule th { font-weight: normal; color: #909399; font-size: 12px; text-align: left; padding: 0 12px 4px 0; }
.rule td { padding: 3px 12px 3px 0; }
.rule td.lack { color: #f56c6c; }
.total { margin-left: 16px; color: #606266; align-self: flex-end; }
.kpis { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 18px; }
.kpi { background: #f6f8fb; border-radius: 8px; padding: 10px 12px; }
.kpi .label { color: #909399; font-size: 12px; }
.kpi .value { font-size: 22px; font-weight: 600; margin-top: 4px; }
.kpi small { font-size: 13px; margin-left: 2px; }
.section-title { font-weight: 600; margin: 16px 0 8px; display: flex; align-items: center; justify-content: space-between; }
.bucket { display: flex; align-items: center; gap: 10px; margin: 6px 0; font-size: 13px; }
.b-name { width: 72px; color: #606266; }
.b-bar { flex: 1; height: 14px; background: #f0f2f5; border-radius: 7px; overflow: hidden; }
.b-fill { height: 100%; background: #1f6feb; border-radius: 7px; min-width: 2px; }
.b-fill.low { background: #f56c6c; }
.b-count { width: 48px; text-align: right; color: #606266; }
.hard { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.hard-content { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hard-rate { font-weight: 600; color: #e6a23c; }
.hard-rate.low { color: #f56c6c; }
.names { display: flex; flex-wrap: wrap; gap: 6px; }
@media (max-width: 768px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
</style>
