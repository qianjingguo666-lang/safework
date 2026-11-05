<template>
  <el-drawer v-model="visible" :title="book ? `《${book.title}》签署与考核` : ''" size="900px" destroy-on-close>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="姓名 / 岗位" clearable style="width: 160px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="签署状态" clearable style="width: 120px" @change="search">
        <el-option v-for="(v, k) in RESP_SIGN_STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-checkbox v-model="query.unassessed" @change="search">只看没考核的</el-checkbox>
      <el-button icon="Search" @click="search">查询</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="userName" label="责任人" width="150" show-overflow-tooltip />
      <el-table-column prop="posts" label="岗位" min-width="120" show-overflow-tooltip />
      <el-table-column label="签署" width="200">
        <template #default="{ row }">
          <el-tag size="small" :type="RESP_SIGN_STATUS[row.status]?.type">{{ RESP_SIGN_STATUS[row.status]?.label }}</el-tag>
          <el-tag v-if="row.late" size="small" type="danger" effect="plain">逾期</el-tag>
          <div v-if="row.signTime" class="muted">{{ row.signTime }}</div>
        </template>
      </el-table-column>
      <el-table-column label="年度考核" width="120">
        <template #default="{ row }">
          <template v-if="row.score != null">{{ row.score }} 分 <el-tag size="small" :type="RESP_GRADES[row.grade]">{{ row.grade }}</el-tag></template>
          <span v-else class="muted">未考核</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="docRef.open(row.id)">查看</el-button>
          <el-button link type="primary" @click="assess(row)">{{ row.score != null ? '改考核' : '考核' }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="aVisible" :title="`年度考核：${cur?.userName}`" width="560px" append-to-body>
      <div v-loading="refLoading" class="ref">
        <div class="ref-title">{{ book?.year }} 年度参考数据（系统自动统计）</div>
        <div v-if="refData" class="ref-grid">
          <div><b>{{ refData.hazardsReported }}</b><span>上报隐患</span></div>
          <div><b>{{ rate(refData.rectifyRate) }}</b><span>按期整改 {{ refData.rectifyOnTime }}/{{ refData.rectifyTotal }}</span></div>
          <div><b>{{ rate(refData.checkRate) }}</b><span>按时检查 {{ refData.checkOnTime }}/{{ refData.checkTotal }}</span></div>
          <div><b>{{ refData.ticketsDone }}</b><span>完成作业票</span></div>
        </div>
        <div v-if="cur?.late" class="warn small">该责任人逾期签署了责任书</div>
      </div>
      <el-form ref="aFormRef" :model="aForm" :rules="aRules" label-width="80px">
        <el-form-item label="得分" prop="score">
          <el-input-number v-model="aForm.score" :min="0" :max="100" />
          <el-tag class="grade" :type="RESP_GRADES[grade]">{{ grade }}</el-tag>
          <span class="muted small">90 优秀 / 80 良好 / 60 合格</span>
        </el-form-item>
        <el-form-item label="考核意见" prop="comment"><el-input v-model="aForm.comment" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssess">保存</el-button>
      </template>
    </el-dialog>

    <SignDoc ref="docRef" />
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assessResp, respReference, respSigns } from '@/api'
import { RESP_GRADES, RESP_SIGN_STATUS } from '@/utils/dict'
import SignDoc from './SignDoc.vue'

const emit = defineEmits(['changed'])
const visible = ref(false)
const loading = ref(false)
const book = ref(null)
const rows = ref([])
const total = ref(0)
const docRef = ref()
const query = reactive({ current: 1, size: 20, keyword: '', status: '', unassessed: false })

const aVisible = ref(false)
const refLoading = ref(false)
const saving = ref(false)
const refData = ref(null)
const cur = ref(null)
const aFormRef = ref()
const aForm = reactive({ score: 85, comment: '' })
const aRules = { score: [{ required: true, message: '请填写得分' }] }
const grade = computed(() => (aForm.score >= 90 ? '优秀' : aForm.score >= 80 ? '良好' : aForm.score >= 60 ? '合格' : '不合格'))

function open(b) {
  book.value = b
  Object.assign(query, { current: 1, keyword: '', status: '', unassessed: false })
  visible.value = true
  load()
}
async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null && v !== false))
    const page = await respSigns(book.value.id, params)
    rows.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
const rate = (v) => (v == null ? '-' : `${v}%`)
async function assess(row) {
  cur.value = row
  aForm.score = row.score ?? 85
  aForm.comment = row.assessComment || ''
  refData.value = null
  aVisible.value = true
  refLoading.value = true
  try {
    refData.value = await respReference(row.id)
  } finally {
    refLoading.value = false
  }
}
async function saveAssess() {
  await aFormRef.value.validate()
  saving.value = true
  try {
    const r = await assessResp(cur.value.id, { score: aForm.score, comment: aForm.comment })
    ElMessage.success(`已保存，考核等级：${r.grade}`)
    aVisible.value = false
    load()
    emit('changed')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
.small { font-size: 12px; }
.warn { color: #e6a23c; }
.el-tag + .el-tag { margin-left: 4px; }
.ref { background: #f6f8fa; border-radius: 6px; padding: 10px 14px; margin-bottom: 14px; }
.ref-title { font-size: 13px; color: #666; margin-bottom: 8px; }
.ref-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.ref-grid div { display: flex; flex-direction: column; }
.ref-grid b { font-size: 20px; }
.ref-grid span { font-size: 12px; color: #888; }
.grade { margin: 0 8px; }
</style>
