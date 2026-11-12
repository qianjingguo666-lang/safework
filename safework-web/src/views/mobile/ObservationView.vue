<template>
  <div class="observation-mobile">
    <button class="report-card" @click="reportVisible = true">
      <el-icon><CirclePlus /></el-icon><span><b>上报未遂事件 / 安全观察</b><small>记录险情、不安全行为和现场安全亮点</small></span>
    </button>

    <div class="filter-row">
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="load">
        <el-option v-for="(label, key) in STATUS_LABELS" :key="key" :value="key" :label="label" />
      </el-select>
    </div>

    <div v-loading="loading">
      <button v-for="item in rows" :key="item.id" class="record-card" @click="openDetail(item.id)">
        <div class="card-top">
          <span class="type">{{ TYPE_LABELS[item.observationType] }}</span>
          <el-tag size="small" :type="STATUS_TYPES[item.status]">{{ STATUS_LABELS[item.status] }}</el-tag>
        </div>
        <b class="title">{{ item.title }}</b>
        <span class="meta">{{ item.location }} · {{ item.occurTime?.replace('T', ' ').slice(0, 16) }}</span>
        <span class="level" :class="item.potentialLevel.toLowerCase()">潜在风险：{{ LEVEL_LABELS[item.potentialLevel] }}</span>
      </button>
      <el-empty v-if="!loading && rows.length === 0" description="还没有观察记录" />
    </div>

    <el-dialog v-model="reportVisible" title="现场上报" fullscreen>
      <el-form ref="reportRef" :model="report" :rules="reportRules" label-position="top">
        <el-form-item label="类型" prop="observationType">
          <div class="chips">
            <button v-for="(label, key) in TYPE_LABELS" :key="key" type="button" :class="{ on: report.observationType === key }" @click="report.observationType = key">{{ label }}</button>
          </div>
        </el-form-item>
        <el-form-item label="标题" prop="title"><el-input v-model="report.title" maxlength="100" placeholder="例如：叉车倒车险些碰到行人" /></el-form-item>
        <el-form-item label="发生时间" prop="occurTime"><el-date-picker v-model="report.occurTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="位置" prop="location"><el-input v-model="report.location" maxlength="100" placeholder="例如：成品仓库西侧通道" /></el-form-item>
        <el-form-item label="潜在风险" prop="potentialLevel">
          <div class="chips">
            <button v-for="(label, key) in LEVEL_LABELS" :key="key" type="button" :class="{ on: report.potentialLevel === key }" @click="report.potentialLevel = key">{{ label }}风险</button>
          </div>
        </el-form-item>
        <el-form-item label="发生经过" prop="description"><el-input v-model="report.description" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="描述当时发生了什么，以及可能导致的后果" /></el-form-item>
        <el-form-item label="现场立即措施"><el-input v-model="report.immediateAction" type="textarea" :rows="3" maxlength="500" placeholder="没有可留空" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reportVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitReport">提交上报</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="观察记录" direction="btt" size="88%">
      <template v-if="detailData">
        <div class="detail">
          <el-tag :type="STATUS_TYPES[detailData.observation.status]">{{ STATUS_LABELS[detailData.observation.status] }}</el-tag>
          <h3>{{ detailData.observation.title }}</h3>
          <p class="muted">{{ TYPE_LABELS[detailData.observation.observationType] }} · {{ detailData.observation.observationNo }}</p>
          <p><b>发生时间：</b>{{ detailData.observation.occurTime?.replace('T', ' ').slice(0, 16) }}</p>
          <p><b>位置：</b>{{ detailData.observation.location }}</p>
          <p><b>潜在风险：</b>{{ LEVEL_LABELS[detailData.observation.potentialLevel] }}</p>
          <p><b>发生经过：</b>{{ detailData.observation.description }}</p>
          <p><b>立即措施：</b>{{ detailData.observation.immediateAction || '暂无' }}</p>
          <p><b>原因分析：</b>{{ detailData.observation.rootCause || '待分析' }}</p>
          <p><b>纠正和预防措施：</b>{{ detailData.observation.correctiveAction || '待制定' }}</p>
          <el-button v-if="detailData.canManage && detailData.observation.hazardId" type="primary" plain @click="router.push('/m/hazard/' + detailData.observation.hazardId)">查看关联隐患</el-button>
          <div v-if="detailData.canManage && detailData.observation.status !== 'CLOSED'" class="actions">
            <el-button type="primary" @click="openAnalysis">分析 / 更新措施</el-button>
            <el-button v-if="detailData.canClose" type="success" @click="closeObservation">关闭记录</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="analysisVisible" title="原因分析与改进措施" fullscreen>
      <el-form ref="analysisRef" :model="analysis" :rules="analysisRules" label-position="top">
        <el-form-item label="原因分析" prop="rootCause"><el-input v-model="analysis.rootCause" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
        <el-form-item label="纠正和预防措施" prop="correctiveAction"><el-input v-model="analysis.correctiveAction" type="textarea" :rows="5" maxlength="1000" /></el-form-item>
        <el-form-item v-if="detailData?.observation.observationType !== 'GOOD_PRACTICE' && !detailData?.observation.hazardId">
          <el-checkbox v-model="analysis.toHazard">同步转入隐患闭环</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="analysisVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAnalysis">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { analyzeSafetyObservation, closeSafetyObservation, reportSafetyObservation, safetyObservationDetail, safetyObservationPage } from '@/api'

const router = useRouter()
const TYPE_LABELS = { NEAR_MISS: '未遂事件', UNSAFE_BEHAVIOR: '不安全行为', UNSAFE_CONDITION: '不安全状态', GOOD_PRACTICE: '安全观察' }
const LEVEL_LABELS = { LOW: '低', MEDIUM: '中', HIGH: '高' }
const STATUS_LABELS = { REPORTED: '待分析', ANALYZING: '分析中', CLOSED: '已关闭' }
const STATUS_TYPES = { REPORTED: 'warning', ANALYZING: 'primary', CLOSED: 'success' }
const query = reactive({ current: 1, size: 30, status: '' })
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const reportVisible = ref(false)
const detailVisible = ref(false)
const analysisVisible = ref(false)
const reportRef = ref()
const analysisRef = ref()
const detailData = ref(null)
const report = reactive({ observationType: 'NEAR_MISS', title: '', occurTime: '', location: '', description: '', potentialLevel: 'MEDIUM', immediateAction: '' })
const analysis = reactive({ rootCause: '', correctiveAction: '', toHazard: false })
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
  } finally { loading.value = false }
}
async function openDetail(id) {
  detailData.value = await safetyObservationDetail(id)
  detailVisible.value = true
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
function openAnalysis() {
  const item = detailData.value.observation
  analysis.rootCause = item.rootCause || ''
  analysis.correctiveAction = item.correctiveAction || ''
  analysis.toHazard = false
  analysisVisible.value = true
}
async function saveAnalysis() {
  await analysisRef.value.validate()
  saving.value = true
  try {
    await analyzeSafetyObservation(detailData.value.observation.id, { ...analysis })
    ElMessage.success(analysis.toHazard ? '已保存并转入隐患闭环' : '分析已保存')
    analysisVisible.value = false
    await openDetail(detailData.value.observation.id)
    await load()
  } finally { saving.value = false }
}
async function closeObservation() {
  await ElMessageBox.confirm('确认关闭？如已转为隐患，需先完成隐患验收。', '关闭确认', { type: 'warning' })
  await closeSafetyObservation(detailData.value.observation.id)
  ElMessage.success('记录已关闭')
  await openDetail(detailData.value.observation.id)
  await load()
}
onMounted(load)
</script>

<style scoped>
.report-card { width: 100%; display: flex; gap: 12px; align-items: center; padding: 16px; border: 0; border-radius: 12px; background: #ecf5ff; color: #1677ff; text-align: left; }
.report-card :deep(.el-icon) { font-size: 28px; }
.report-card span { display: flex; flex-direction: column; gap: 4px; }
.report-card small { color: #667085; }
.filter-row { margin: 12px 0; }
.filter-row :deep(.el-select) { width: 100%; }
.record-card { width: 100%; display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; padding: 14px; border: 1px solid #ebeef5; border-radius: 10px; background: #fff; text-align: left; }
.card-top { display: flex; justify-content: space-between; align-items: center; }
.type, .muted, .meta { color: #86909c; font-size: 12px; }
.title { font-size: 16px; color: #303133; }
.meta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.level { font-size: 12px; }
.level.high { color: #f56c6c; }
.level.medium { color: #e6a23c; }
.level.low { color: #909399; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chips button { border: 1px solid #dcdfe6; border-radius: 18px; padding: 8px 12px; color: #606266; background: #fff; }
.chips button.on { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.detail p { line-height: 1.7; color: #606266; white-space: pre-wrap; }
.detail h3 { margin: 12px 0 4px; }
.actions { display: flex; gap: 8px; margin-top: 20px; }
</style>
