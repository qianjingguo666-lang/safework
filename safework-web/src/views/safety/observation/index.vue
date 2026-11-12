<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="编号 / 标题 / 位置" clearable style="width: 220px" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" placeholder="处理状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(label, key) in STATUS_LABELS" :key="key" :value="key" :label="label" />
      </el-select>
      <el-select v-model="query.observationType" placeholder="观察类型" clearable style="width: 160px" @change="search">
        <el-option v-for="(label, key) in TYPE_LABELS" :key="key" :value="key" :label="label" />
      </el-select>
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" icon="Plus" @click="reportVisible = true">现场上报</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="observationNo" label="编号" width="170" />
      <el-table-column label="类型" width="125">
        <template #default="{ row }">{{ TYPE_LABELS[row.observationType] || row.observationType }}</template>
      </el-table-column>
      <el-table-column prop="title" label="事件 / 观察" min-width="220" show-overflow-tooltip />
      <el-table-column label="潜在风险" width="105">
        <template #default="{ row }"><el-tag :type="LEVEL_TYPES[row.potentialLevel]">{{ LEVEL_LABELS[row.potentialLevel] }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
      <el-table-column prop="reporterName" label="上报人" width="120" />
      <el-table-column prop="occurTime" label="发生时间" width="170" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="STATUS_TYPES[row.status]">{{ STATUS_LABELS[row.status] }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-if="canManage && row.status !== 'CLOSED'" link type="primary" @click="openAnalysis(row)">分析</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        layout="total, prev, pager, next, sizes" :page-sizes="[10, 20, 50]" @change="load" />
    </div>

    <el-dialog v-model="reportVisible" title="现场上报" width="640px" destroy-on-close>
      <el-form ref="reportRef" :model="report" :rules="reportRules" label-width="110px">
        <el-form-item label="观察类型" prop="observationType">
          <el-select v-model="report.observationType" style="width: 100%">
            <el-option v-for="(label, key) in TYPE_LABELS" :key="key" :value="key" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title"><el-input v-model="report.title" maxlength="100" /></el-form-item>
        <el-form-item label="发生时间" prop="occurTime">
          <el-date-picker v-model="report.occurTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model="report.location" maxlength="100" /></el-form-item>
        <el-form-item label="潜在风险" prop="potentialLevel">
          <el-radio-group v-model="report.potentialLevel">
            <el-radio-button label="LOW">低</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="HIGH">高</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="经过描述" prop="description"><el-input v-model="report.description" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="现场立即措施"><el-input v-model="report.immediateAction" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReport">提交上报</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="观察记录详情" size="560px">
      <template v-if="detailData">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="编号">{{ detailData.observation.observationNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ TYPE_LABELS[detailData.observation.observationType] }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detailData.observation.title }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ detailData.observation.occurTime }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detailData.observation.location }}</el-descriptions-item>
          <el-descriptions-item label="潜在风险">{{ LEVEL_LABELS[detailData.observation.potentialLevel] }}</el-descriptions-item>
          <el-descriptions-item label="经过描述">{{ detailData.observation.description }}</el-descriptions-item>
          <el-descriptions-item label="立即措施">{{ detailData.observation.immediateAction || '暂无' }}</el-descriptions-item>
          <el-descriptions-item label="原因分析">{{ detailData.observation.rootCause || '待分析' }}</el-descriptions-item>
          <el-descriptions-item label="纠正和预防措施">{{ detailData.observation.correctiveAction || '待制定' }}</el-descriptions-item>
          <el-descriptions-item label="上报人">{{ detailData.observation.reporterName }}</el-descriptions-item>
          <el-descriptions-item label="分析人">{{ detailData.observation.analystName || '待分析' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.canManage && detailData.observation.hazardId" label="关联隐患">
            <el-button link type="primary" @click="router.push('/safety/hazard?detail=' + detailData.observation.hazardId)">打开隐患 #{{ detailData.observation.hazardId }}</el-button>
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button v-if="detailData.canManage && detailData.observation.status !== 'CLOSED'" type="primary" @click="openAnalysis(detailData.observation)">分析 / 更新措施</el-button>
          <el-button v-if="detailData.canClose" type="success" @click="closeObservation">关闭记录</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="analysisVisible" title="原因分析与改进措施" width="620px" destroy-on-close>
      <el-form ref="analysisRef" :model="analysis" :rules="analysisRules" label-width="130px">
        <el-form-item label="原因分析" prop="rootCause"><el-input v-model="analysis.rootCause" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="纠正和预防措施" prop="correctiveAction"><el-input v-model="analysis.correctiveAction" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item v-if="detailData?.observation.observationType !== 'GOOD_PRACTICE' && !detailData?.observation.hazardId">
          <el-checkbox v-model="analysis.toHazard">同步转入隐患闭环</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="analysisVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAnalysis">保存分析</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { analyzeSafetyObservation, closeSafetyObservation, reportSafetyObservation, safetyObservationDetail, safetyObservationPage } from '@/api'

const router = useRouter()
const store = useUserStore()
const canManage = computed(() => store.hasPerm('safety:observation:manage'))
const TYPE_LABELS = { NEAR_MISS: '未遂事件', UNSAFE_BEHAVIOR: '不安全行为', UNSAFE_CONDITION: '不安全状态', GOOD_PRACTICE: '安全观察' }
const LEVEL_LABELS = { LOW: '低', MEDIUM: '中', HIGH: '高' }
const LEVEL_TYPES = { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger' }
const STATUS_LABELS = { REPORTED: '待分析', ANALYZING: '分析中', CLOSED: '已关闭' }
const STATUS_TYPES = { REPORTED: 'warning', ANALYZING: 'primary', CLOSED: 'success' }

const query = reactive({ current: 1, size: 20, keyword: '', status: '', observationType: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const reportVisible = ref(false)
const detailVisible = ref(false)
const analysisVisible = ref(false)
const detailData = ref(null)
const reportRef = ref()
const analysisRef = ref()
const report = reactive({ observationType: 'NEAR_MISS', title: '', occurTime: '', location: '', description: '', potentialLevel: 'MEDIUM', immediateAction: '' })
const analysis = reactive({ id: null, rootCause: '', correctiveAction: '', toHazard: false })
const reportRules = {
  observationType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  occurTime: [{ required: true, message: '请选择发生时间', trigger: 'change' }],
  location: [{ required: true, message: '请填写位置', trigger: 'blur' }],
  description: [{ required: true, message: '请描述发生经过', trigger: 'blur' }]
}
const analysisRules = {
  rootCause: [{ required: true, message: '请填写原因分析', trigger: 'blur' }],
  correctiveAction: [{ required: true, message: '请填写纠正和预防措施', trigger: 'blur' }]
}
async function load() {
  loading.value = true
  try {
    const data = await safetyObservationPage(query)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}
function search() { query.current = 1; load() }
async function openDetail(id) {
  detailData.value = await safetyObservationDetail(id)
  detailVisible.value = true
}
function openAnalysis(item) {
  analysis.id = item.id
  analysis.rootCause = item.rootCause || ''
  analysis.correctiveAction = item.correctiveAction || ''
  analysis.toHazard = false
  analysisVisible.value = true
}
async function submitReport() {
  await reportRef.value.validate()
  saving.value = true
  try {
    await reportSafetyObservation({ ...report })
    ElMessage.success('上报成功')
    reportVisible.value = false
    Object.assign(report, { observationType: 'NEAR_MISS', title: '', occurTime: '', location: '', description: '', potentialLevel: 'MEDIUM', immediateAction: '' })
    await load()
  } finally { saving.value = false }
}
async function saveAnalysis() {
  await analysisRef.value.validate()
  saving.value = true
  try {
    await analyzeSafetyObservation(analysis.id, { rootCause: analysis.rootCause, correctiveAction: analysis.correctiveAction, toHazard: analysis.toHazard })
    ElMessage.success(analysis.toHazard ? '分析已保存，并已转入隐患闭环' : '分析已保存')
    analysisVisible.value = false
    await openDetail(analysis.id)
    await load()
  } finally { saving.value = false }
}
async function closeObservation() {
  await ElMessageBox.confirm('确认关闭这条观察记录？关联隐患必须先完成验收。', '关闭确认', { type: 'warning' })
  await closeSafetyObservation(detailData.value.observation.id)
  ElMessage.success('记录已关闭')
  await openDetail(detailData.value.observation.id)
  await load()
}
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.detail-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 18px; }
</style>
