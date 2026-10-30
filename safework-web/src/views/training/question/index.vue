<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="题干关键词" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.type" placeholder="题型" clearable style="width: 110px" @change="search">
        <el-option v-for="(v, k) in QUESTION_TYPES" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="query.category" placeholder="分类" clearable filterable style="width: 140px" @change="search">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="search">
        <el-option v-for="(v, k) in QUESTION_STATUS" :key="k" :label="v.label" :value="Number(k)" />
      </el-select>
      <el-select v-model="query.source" placeholder="来源" clearable style="width: 110px" @change="search">
        <el-option label="人工录入" value="MANUAL" /><el-option label="AI 生成" value="AI" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <template v-if="canEdit && selected.length">
        <el-button type="success" plain icon="Select" @click="approve(selected)">审核通过（{{ selected.length }}）</el-button>
        <el-button type="danger" plain icon="Delete" @click="remove(selected)">删除</el-button>
      </template>
      <el-button v-perm="'training:question:ai'" type="warning" icon="MagicStick" @click="openAi">AI 出题</el-button>
      <el-button v-perm="'training:question:edit'" type="primary" icon="Plus" @click="openForm()">新增题目</el-button>
    </div>

    <el-alert v-if="pendingCount" type="warning" show-icon :closable="false" class="pending">
      <template #title>
        有 <b>{{ pendingCount }}</b> 道 AI 生成的题目待审核，审核通过后才会用于考试。
        <el-button link type="primary" @click="showPending">只看待审核</el-button>
      </template>
    </el-alert>

    <el-table v-loading="loading" :data="list" row-key="id" @selection-change="(rows) => (selected = rows.map((r) => r.id))">
      <el-table-column v-if="canEdit" type="selection" width="42" />
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <div v-for="(o, i) in row.options" :key="i" class="opt" :class="{ right: row.answer.includes(letter(i)) }">
              {{ letter(i) }}. {{ o }} <el-icon v-if="row.answer.includes(letter(i))"><Check /></el-icon>
            </div>
            <div v-if="row.analysis" class="analysis"><b>解析：</b>{{ row.analysis }}</div>
            <div v-if="row.sourceDoc" class="muted">出题依据：《{{ row.sourceDoc }}》</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="题型" width="90">
        <template #default="{ row }"><el-tag :type="QUESTION_TYPES[row.type]?.type" effect="plain">{{ row.typeLabel }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="content" label="题干" min-width="300" show-overflow-tooltip />
      <el-table-column label="答案" width="70"><template #default="{ row }"><b>{{ answerText(row) }}</b></template></el-table-column>
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column label="来源" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.source === 'AI'" type="warning" effect="plain" size="small">AI 生成</el-tag>
          <span v-else class="muted">人工</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><el-tag :type="QUESTION_STATUS[row.status]?.type">{{ QUESTION_STATUS[row.status]?.label }}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="canEdit" label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="success" @click="approve([row.id])">通过</el-button>
          <el-button link type="primary" @click="openForm(row)">修改</el-button>
          <el-button v-if="row.status === 1" link type="info" @click="setStatus(row, 2)">停用</el-button>
          <el-button v-if="row.status === 2" link type="primary" @click="setStatus(row, 1)">启用</el-button>
          <el-button link type="danger" @click="remove([row.id])">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <!-- 新增 / 修改题目 -->
    <el-dialog v-model="formVisible" :title="editId ? '修改题目' : '新增题目'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-row :gutter="12">
          <el-col :span="14">
            <el-form-item label="题型" prop="type">
              <el-radio-group v-model="form.type" @change="onTypeChange">
                <el-radio-button v-for="(v, k) in QUESTION_TYPES" :key="k" :value="k">{{ v.label }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="分类" prop="category" label-width="50px">
              <el-select v-model="form.category" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="c in allCategories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="题干" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="选项" required>
          <div class="options">
            <template v-if="form.type === 'JUDGE'">
              <el-radio-group v-model="form.answer">
                <el-radio value="A">正确</el-radio><el-radio value="B">错误</el-radio>
              </el-radio-group>
            </template>
            <template v-else>
              <div v-for="(o, i) in form.options" :key="i" class="option-row">
                <el-checkbox v-if="form.type === 'MULTI'" :model-value="form.answer.includes(letter(i))" @change="toggleAnswer(i)">{{ letter(i) }}</el-checkbox>
                <el-radio v-else :model-value="form.answer" :value="letter(i)" @change="form.answer = letter(i)">{{ letter(i) }}</el-radio>
                <el-input v-model="form.options[i]" maxlength="200" placeholder="选项内容" />
                <el-button link type="danger" icon="Delete" :disabled="form.options.length <= (form.type === 'MULTI' ? 3 : 2)" @click="removeOption(i)" />
              </div>
              <el-button link type="primary" icon="Plus" :disabled="form.options.length >= 6" @click="form.options.push('')">添加选项</el-button>
              <div class="muted">勾选的是正确答案{{ form.type === 'MULTI' ? '（至少 2 个）' : '' }}</div>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="解析" prop="analysis">
          <el-input v-model="form.analysis" type="textarea" :rows="2" maxlength="500" placeholder="答题后展示，建议写明制度依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 出题 -->
    <el-dialog v-model="aiVisible" title="AI 出题" width="620px" :close-on-click-modal="!generating">
      <el-alert type="info" :closable="false" show-icon class="ai-tip"
                title="AI 根据知识库里的制度文件出题，每道题都会自动校验格式、去掉重复，然后以【待审核】状态入库。请逐题检查后再审核通过。" />
      <el-form :model="aiForm" label-width="90px">
        <el-form-item label="制度文件" required>
          <el-select v-model="aiForm.documentId" placeholder="选择知识库里的文件" style="width: 100%">
            <el-option v-for="d in docs" :key="d.id" :label="d.title" :value="d.id" :disabled="d.status !== 'READY'" />
          </el-select>
          <div v-if="!docs.length" class="muted">知识库里还没有文件，请先在【AI 助手 → 知识库】上传安全制度</div>
        </el-form-item>
        <el-form-item label="题目分类" required>
          <el-select v-model="aiForm.category" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="c in allCategories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="出题重点">
          <el-input v-model="aiForm.topic" maxlength="50" placeholder="可选，如：气体检测、应急救援" />
        </el-form-item>
        <el-form-item label="题目数量">
          <div class="counts">
            <span>单选</span><el-input-number v-model="aiForm.singleCount" :min="0" :max="10" size="small" />
            <span>多选</span><el-input-number v-model="aiForm.multiCount" :min="0" :max="10" size="small" />
            <span>判断</span><el-input-number v-model="aiForm.judgeCount" :min="0" :max="10" size="small" />
          </div>
        </el-form-item>
      </el-form>
      <div v-if="generating" class="generating">
        <el-icon class="is-loading"><Loading /></el-icon> AI 正在阅读制度并出题，大约需要 20~60 秒，请不要关闭窗口…
      </div>
      <template v-if="aiResult">
        <el-alert :type="aiResult.saved ? 'success' : 'warning'" :closable="false" show-icon class="ai-result"
                  :title="`生成 ${aiResult.generated} 道，入库 ${aiResult.saved} 道（待审核），用时 ${(aiResult.costMs / 1000).toFixed(1)} 秒`" />
        <el-table v-if="aiResult.skipped.length" :data="aiResult.skipped" size="small" max-height="200">
          <el-table-column prop="content" label="未入库的题" show-overflow-tooltip />
          <el-table-column prop="reason" label="原因" width="180" />
        </el-table>
      </template>
      <template #footer>
        <el-button :disabled="generating" @click="aiVisible = false">关闭</el-button>
        <el-button v-if="aiResult?.saved" type="success" @click="reviewNow">去审核</el-button>
        <el-button type="primary" :loading="generating" :disabled="!aiForm.documentId || !aiForm.category" @click="generate">开始出题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  aiGenerateQuestions, approveQuestions, changeQuestionStatus, createQuestion, pageKnowledge, pageQuestions,
  questionCategories, removeQuestions, updateQuestion
} from '@/api'
import { QUESTION_CATEGORIES, QUESTION_STATUS, QUESTION_TYPES, letter } from '@/utils/dict'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('training:question:edit'))
const query = reactive({ current: 1, size: 10, keyword: '', type: '', category: '', status: null, source: '' })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const selected = ref([])
const categories = ref([])
const pendingCount = ref(0)
const allCategories = computed(() => [...new Set([...QUESTION_CATEGORIES, ...categories.value])])

async function load() {
  loading.value = true
  try {
    const data = await pageQuestions(query)
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
  pendingCount.value = query.status === 0 ? total.value : (await pageQuestions({ current: 1, size: 1, status: 0 })).total
}
function search() {
  query.current = 1
  load()
}
function showPending() {
  Object.assign(query, { status: 0, source: '', type: '', category: '', keyword: '' })
  search()
}
async function loadCategories() {
  categories.value = await questionCategories()
}
function answerText(row) {
  if (row.type === 'JUDGE') return row.answer === 'A' ? '对' : '错'
  return row.answer
}

async function approve(ids) {
  const n = await approveQuestions(ids)
  ElMessage.success(n ? `已审核通过 ${n} 道` : '选中的题目没有待审核的')
  load()
}
async function remove(ids) {
  await ElMessageBox.confirm(`确定删除 ${ids.length} 道题目？已经生成的试卷不受影响。`, '删除题目', { type: 'warning' })
  await removeQuestions(ids)
  ElMessage.success('已删除')
  load()
}
async function setStatus(row, status) {
  await changeQuestionStatus(row.id, status)
  row.status = status
  ElMessage.success(status === 1 ? '已启用' : '已停用，不会再被抽到')
}

// ---------- 新增 / 修改 ----------
const formVisible = ref(false)
const formRef = ref()
const editId = ref(null)
const saving = ref(false)
const form = reactive({ type: 'SINGLE', category: '', content: '', options: [], answer: '', analysis: '' })
const rules = {
  category: [{ required: true, message: '请填写分类', trigger: 'change' }],
  content: [{ required: true, message: '请填写题干', trigger: 'blur' }]
}
function openForm(row) {
  editId.value = row?.id || null
  Object.assign(form, row
    ? { type: row.type, category: row.category, content: row.content, options: [...row.options], answer: row.answer, analysis: row.analysis || '' }
    : { type: 'SINGLE', category: query.category || QUESTION_CATEGORIES[0], content: '', options: ['', '', '', ''], answer: '', analysis: '' })
  formVisible.value = true
}
function onTypeChange(t) {
  form.answer = ''
  if (t === 'JUDGE') form.options = ['正确', '错误']
  else if (form.options.length < 4 || form.options[0] === '正确') form.options = ['', '', '', '']
}
function toggleAnswer(i) {
  const l = letter(i)
  form.answer = form.answer.includes(l) ? form.answer.replace(l, '') : [...form.answer + l].sort().join('')
}
function removeOption(i) {
  form.options.splice(i, 1)
  form.answer = ''
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const data = { ...form }
    if (editId.value) await updateQuestion(editId.value, data)
    else await createQuestion(data)
    ElMessage.success('已保存')
    formVisible.value = false
    load()
    loadCategories()
  } finally {
    saving.value = false
  }
}

// ---------- AI 出题 ----------
const aiVisible = ref(false)
const docs = ref([])
const generating = ref(false)
const aiResult = ref(null)
const aiForm = reactive({ documentId: null, category: QUESTION_CATEGORIES[0], topic: '', singleCount: 3, multiCount: 1, judgeCount: 2 })
async function openAi() {
  aiResult.value = null
  aiVisible.value = true
  docs.value = (await pageKnowledge({ current: 1, size: 100 })).records
  if (!aiForm.documentId && docs.value.length) aiForm.documentId = docs.value[0].id
}
async function generate() {
  generating.value = true
  aiResult.value = null
  try {
    aiResult.value = await aiGenerateQuestions(aiForm)
    loadCategories()
    load()
  } finally {
    generating.value = false
  }
}
function reviewNow() {
  aiVisible.value = false
  showPending()
}

onMounted(() => {
  load()
  loadCategories()
})
</script>

<style scoped>
.pending { margin-bottom: 12px; }
.expand { padding: 4px 48px 8px; line-height: 1.9; }
.opt.right { color: #67c23a; font-weight: 600; }
.analysis { margin-top: 4px; color: #606266; }
.muted { color: #909399; font-size: 12px; }
.options { width: 100%; }
.option-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.option-row .el-checkbox, .option-row .el-radio { margin-right: 0; width: 44px; }
.counts { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.counts span { color: #606266; }
.ai-tip, .ai-result { margin-bottom: 12px; }
.generating { color: #1f6feb; display: flex; align-items: center; gap: 6px; margin: 4px 0 8px 90px; }
</style>
