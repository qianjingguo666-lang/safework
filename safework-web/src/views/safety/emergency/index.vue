<template>
  <div class="emergency-page">
    <div v-if="overview?.responding" class="responding-bar" @click="filterResponding">
      <el-icon class="pulse"><AlarmClock /></el-icon>
      <b>{{ overview.responding }} 起事件正在应急响应中</b>
      <span>点击查看</span>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="编号 / 标题 / 地点" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
          <el-option v-for="(v, k) in EMERGENCY_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.accidentType" placeholder="事故类型" clearable style="width: 130px" @change="search">
          <el-option v-for="(v, k) in ACCIDENT_TYPES" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button icon="Search" @click="search">查询</el-button>
        <div class="spacer" />
        <span v-if="overview" class="stat">今年共 {{ overview.reportedThisYear }} 起</span>
        <el-button v-perm="'emergency:event:report'" type="danger" icon="Warning" @click="openReport">事故快报</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe :row-class-name="rowClass" @row-click="(r) => detailRef.open(r.id)">
        <el-table-column prop="eventNo" label="编号" width="160" />
        <el-table-column prop="title" label="事件" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100"><template #default="{ row }">{{ ACCIDENT_TYPES[row.accidentType] }}</template></el-table-column>
        <el-table-column label="等级" width="110">
          <template #default="{ row }"><el-tag size="small" :type="EMERGENCY_LEVELS[row.level]?.type" effect="plain">{{ EMERGENCY_LEVELS[row.level]?.label }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="location" label="地点" min-width="150" show-overflow-tooltip />
        <el-table-column label="伤亡" width="110">
          <template #default="{ row }">{{ casualty(row) }}</template>
        </el-table-column>
        <el-table-column label="发生时间" width="150"><template #default="{ row }">{{ (row.occurTime || '').slice(0, 16) }}</template></el-table-column>
        <el-table-column label="状态" width="100" fixed="right">
          <template #default="{ row }"><el-tag :type="EMERGENCY_STATUS[row.status]?.type" :effect="row.status === 'RESPONDING' ? 'dark' : 'light'">{{ EMERGENCY_STATUS[row.status]?.label }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="reportVisible" title="事故快报" width="min(600px, 94vw)">
      <el-alert type="error" :closable="false" show-icon title="先报告，再完善：知道多少报多少，提交后会立即通知安全员。有人员受伤先拨打 120" class="report-tip" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="发生了什么" prop="title"><el-input v-model="form.title" maxlength="100" placeholder="如：污水池清淤时有人晕倒" /></el-form-item>
        <el-row :gutter="12">
          <el-col :xs="24" :span="12">
            <el-form-item label="事故类型" prop="accidentType">
              <el-select v-model="form.accidentType" style="width: 100%"><el-option v-for="(v, k) in ACCIDENT_TYPES" :key="k" :label="v" :value="k" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :span="12">
            <el-form-item label="事发时间" prop="occurTime">
              <el-date-picker v-model="form.occurTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="(d) => d > new Date()" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地点" prop="location"><el-input v-model="form.location" maxlength="100" placeholder="如：污水处理站调节池" /></el-form-item>
        <el-form-item label="受限空间">
          <el-select v-model="form.spaceId" clearable filterable placeholder="可选：事故发生在某个受限空间" style="width: 100%" @change="onSpace">
            <el-option v-for="s in spaces" :key="s.id" :label="`${s.spaceCode} ${s.spaceName}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="伤亡情况">
          <div class="casualty">
            <span>死亡</span><el-input-number v-model="form.dead" :min="0" :max="9999" controls-position="right" />
            <span>重伤</span><el-input-number v-model="form.seriousInjured" :min="0" :max="9999" controls-position="right" />
            <span>轻伤</span><el-input-number v-model="form.minorInjured" :min="0" :max="9999" controls-position="right" />
          </div>
        </el-form-item>
        <el-form-item label="事故等级" prop="level">
          <el-radio-group v-model="form.level">
            <el-radio v-for="(v, k) in EMERGENCY_LEVELS" :key="k" :value="k">{{ v.label }}</el-radio>
          </el-radio-group>
          <div v-if="levelHint" class="level-hint">{{ levelHint }}</div>
        </el-form-item>
        <el-form-item label="情况描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" placeholder="现场情况、已经采取的措施" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doReport">立即上报</el-button>
      </template>
    </el-dialog>

    <EmergencyDetail ref="detailRef" @changed="reload" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { emergencyOverview, emergencyPage, reportEmergency, spaceOptions } from '@/api'
import { ACCIDENT_TYPES, EMERGENCY_LEVELS, EMERGENCY_STATUS } from '@/utils/dict'
import EmergencyDetail from './EmergencyDetail.vue'

const route = useRoute()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const overview = ref(null)
const detailRef = ref()
const query = reactive({ current: 1, size: 10, keyword: '', status: '', accidentType: '' })

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await emergencyPage(params)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const loadOverview = async () => { overview.value = await emergencyOverview() }
function search() {
  query.current = 1
  load()
}
function reload() {
  load()
  loadOverview()
}
function filterResponding() {
  query.status = 'RESPONDING'
  search()
}
const rowClass = ({ row }) => (row.status === 'RESPONDING' ? 'row-responding' : '')
const casualty = (r) => (r.dead || r.seriousInjured || r.minorInjured ? `亡${r.dead} 重${r.seriousInjured} 轻${r.minorInjured}` : '无')

// ---------------- 事故快报 ----------------
const reportVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const spaces = ref([])
const now = () => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return d.toISOString().slice(0, 19).replace('T', ' ')
}
const empty = () => ({ title: '', accidentType: '', level: 'GENERAL', location: '', spaceId: null, description: '', dead: 0, seriousInjured: 0, minorInjured: 0, occurTime: now() })
const form = reactive(empty())
const rules = {
  title: [{ required: true, message: '请用一句话说明发生了什么', trigger: 'blur' }],
  accidentType: [{ required: true, message: '请选择事故类型' }],
  level: [{ required: true, message: '请选择事故等级' }],
  location: [{ required: true, message: '请填写地点', trigger: 'blur' }],
  occurTime: [{ required: true, message: '请选择事发时间' }]
}
// 和后端 EmergencyRules.minLevel 一致：伤亡人数决定最低等级
const LEVEL_ORDER = ['GENERAL', 'LARGER', 'MAJOR', 'EXTRA']
const minLevel = computed(() => {
  const { dead, seriousInjured: s } = form
  if (dead >= 30 || s >= 100) return 'EXTRA'
  if (dead >= 10 || s >= 50) return 'MAJOR'
  if (dead >= 3 || s >= 10) return 'LARGER'
  return 'GENERAL'
})
const levelHint = computed(() => (LEVEL_ORDER.indexOf(form.level) < LEVEL_ORDER.indexOf(minLevel.value)
  ? `按伤亡人数至少是${EMERGENCY_LEVELS[minLevel.value].label}，提交后会自动调高` : ''))
watch(minLevel, (v) => { if (LEVEL_ORDER.indexOf(form.level) < LEVEL_ORDER.indexOf(v)) form.level = v })

async function openReport() {
  Object.assign(form, empty())
  reportVisible.value = true
  formRef.value?.clearValidate()
  spaces.value = await spaceOptions()
}
function onSpace(id) {
  const s = spaces.value.find((x) => x.id === id)
  if (s && !form.location) form.location = `${s.location} ${s.spaceName}`
}
async function doReport() {
  await formRef.value.validate()
  saving.value = true
  try {
    const id = await reportEmergency(form)
    ElMessage.success('已上报，安全员会立即收到通知')
    reportVisible.value = false
    reload()
    detailRef.value.open(id)
  } finally {
    saving.value = false
  }
}

onMounted(reload)
// 从消息点进来：/safety/emergency?detail=ID
const openFromQuery = () => { if (route.query.detail) detailRef.value?.open(route.query.detail) }
onMounted(openFromQuery)
watch(() => route.query.detail, openFromQuery)
</script>

<style scoped>
.responding-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; padding: 12px 16px; border-radius: 8px; background: #fef0f0; border: 1px solid #fbc4c4; color: #c45656; cursor: pointer; }
.responding-bar span { margin-left: auto; font-size: 13px; }
.pulse { font-size: 20px; animation: pulse 1.2s infinite; }
@keyframes pulse { 50% { opacity: .35; } }
.stat { color: #999; font-size: 13px; }
:deep(.el-table__row) { cursor: pointer; }
:deep(.row-responding td) { background: #fef6f6 !important; }
.report-tip { margin-bottom: 16px; }
.casualty { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.casualty .el-input-number { width: 100px; }
.casualty span { color: #666; }
.level-hint { width: 100%; color: #e6a23c; font-size: 12px; line-height: 1.6; }
</style>
