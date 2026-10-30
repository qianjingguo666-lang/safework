<template>
  <div class="risk-page">
    <!-- 统计 -->
    <div v-if="overview" class="kpis">
      <div v-for="(g, k) in RISK_GRADES" :key="k" class="kpi grade" :class="{ active: query.riskLevel === k }"
           :style="{ '--c': g.color }" @click="filterLevel(k)">
        <div class="label"><i />{{ g.label }}</div>
        <div class="value">{{ overview.levels[k] || 0 }}</div>
        <div class="sub">{{ g.control }}管控</div>
      </div>
      <div class="kpi" :class="{ active: query.overdue, warn: overview.overdue }" @click="filterOverdue">
        <div class="label">巡检逾期</div>
        <div class="value">{{ overview.overdue }}</div>
        <div class="sub">今天到期 {{ overview.dueToday }} 个</div>
      </div>
      <div class="kpi">
        <div class="label">近 30 天巡检</div>
        <div class="value">{{ overview.checks30d }}</div>
        <div class="sub">发现措施失效 {{ overview.abnormal30d }} 次</div>
      </div>
    </div>

    <div class="page-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="风险清单" name="list" />
        <el-tab-pane label="四色分布" name="map" />
      </el-tabs>

      <template v-if="tab === 'list'">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="编号 / 名称 / 位置" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.riskLevel" placeholder="风险等级" clearable style="width: 120px" @change="search">
            <el-option v-for="(g, k) in RISK_GRADES" :key="k" :label="g.label" :value="k" />
          </el-select>
          <el-select v-model="query.pointType" placeholder="类型" clearable style="width: 120px" @change="search">
            <el-option v-for="t in RISK_POINT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <el-checkbox v-model="query.overdue" @change="search">只看巡检逾期</el-checkbox>
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <el-button icon="Download" :loading="exporting" @click="doExport">导出管控清单</el-button>
          <el-button v-perm="'risk:point:edit'" type="primary" icon="Plus" @click="openForm()">新增风险点</el-button>
        </div>
        <el-table v-loading="loading" :data="list" stripe>
          <el-table-column prop="pointCode" label="编号" width="90" />
          <el-table-column label="风险点" min-width="170">
            <template #default="{ row }"><el-link type="primary" @click="openDetail(row)">{{ row.pointName }}</el-link></template>
          </el-table-column>
          <el-table-column prop="pointType" label="类型" width="80" />
          <el-table-column prop="location" label="位置" width="110" show-overflow-tooltip />
          <el-table-column prop="responsibleName" label="责任人" width="130" show-overflow-tooltip />
          <el-table-column label="LEC 评估" width="150">
            <template #default="{ row }"><span class="lec">{{ num(row.lecL) }} × {{ num(row.lecE) }} × {{ num(row.lecC) }} = <b>{{ num(row.lecD) }}</b></span></template>
          </el-table-column>
          <el-table-column label="风险等级" width="100"><template #default="{ row }"><RiskBadge :level="row.riskLevel" /></template></el-table-column>
          <el-table-column label="下次巡检" width="160">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <div class="small">{{ (row.nextCheckTime || '').slice(0, 16) }}</div>
                <el-tag v-if="row.checkState !== 'UPCOMING'" size="small" :type="CHECK_STATES[row.checkState].type">{{ CHECK_STATES[row.checkState].label }}</el-tag>
                <span v-else class="muted">每 {{ row.checkCycleDays }} 天</span>
              </template>
              <el-tag v-else type="info" size="small">已停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openForm(row)">修改</el-button>
              <el-button link :type="row.status === 1 ? 'info' : 'primary'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </template>

      <!-- 四色分布：按位置分组，每个风险点一个色块 -->
      <template v-else>
        <div class="legend">
          <span v-for="(g, k) in RISK_GRADES" :key="k"><i :style="{ background: g.color }" />{{ g.label }}</span>
          <span><i class="overdue-mark" />巡检逾期</span>
        </div>
        <div class="map">
          <div v-for="grp in overview?.groups || []" :key="grp.location" class="area">
            <div class="area-title">{{ grp.location }} <span class="muted">{{ grp.points.length }} 个</span></div>
            <div class="tiles">
              <div v-for="p in grp.points" :key="p.id" class="tile" :class="{ overdue: p.state === 'OVERDUE' }"
                   :style="{ background: RISK_GRADES[p.level].color, color: RISK_GRADES[p.level].text }" @click="openDetail(p)">
                <div class="tile-code">{{ p.code }}<span v-if="p.state === 'OVERDUE'" class="tile-flag">逾期</span></div>
                <div class="tile-name">{{ p.name }}</div>
              </div>
            </div>
          </div>
          <el-empty v-if="overview && !overview.groups.length" description="还没有风险点" />
        </div>
      </template>
    </div>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="detail?.point.pointName" size="620px">
      <template v-if="detail">
        <div class="d-head">
          <RiskBadge :level="detail.point.riskLevel" />
          <span>D = {{ num(detail.point.lecL) }} × {{ num(detail.point.lecE) }} × {{ num(detail.point.lecC) }} = <b>{{ num(detail.point.lecD) }}</b></span>
          <span class="muted">{{ RISK_GRADES[detail.point.riskLevel].control }}管控 · 每 {{ detail.point.checkCycleDays }} 天巡检</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ detail.point.pointCode }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.point.pointType }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detail.point.location }}</el-descriptions-item>
          <el-descriptions-item label="责任部门">{{ detail.point.deptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ detail.point.responsibleName }}</el-descriptions-item>
          <el-descriptions-item label="受限空间">{{ detail.point.spaceName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="危险有害因素" :span="2">{{ detail.point.hazardFactors || '-' }}</el-descriptions-item>
          <el-descriptions-item label="可能导致" :span="2">{{ detail.point.possibleAccidents || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上次巡检">{{ (detail.point.lastCheckTime || '-').slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item label="下次巡检">
            {{ (detail.point.nextCheckTime || '-').slice(0, 16) }}
            <el-tag v-if="detail.point.checkState === 'OVERDUE'" size="small" type="danger">逾期</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="section">管控措施</div>
        <ol class="measures"><li v-for="m in detail.measures" :key="m">{{ m }}</li></ol>
        <div class="section">最近巡检</div>
        <el-empty v-if="!detail.checks.length" description="还没有巡检记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="c in detail.checks" :key="c.id" :timestamp="c.checkTime" :type="c.result === 'NORMAL' ? 'success' : 'danger'">
            <b>{{ c.checkerName }}</b>
            <el-tag size="small" :type="c.result === 'NORMAL' ? 'success' : 'danger'">{{ c.result === 'NORMAL' ? '措施有效' : '有措施失效' }}</el-tag>
            <el-link v-if="c.hazardNo" type="primary" class="hz" @click="goHazard(c.hazardId)">已转隐患 {{ c.hazardNo }}</el-link>
            <div v-for="it in failedItems(c)" :key="it.measure" class="failed">✗ {{ it.measure }}<span v-if="it.note">（{{ it.note }}）</span></div>
            <div v-if="c.remark" class="muted">备注：{{ c.remark }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 新增 / 修改：LEC 评估 -->
    <el-dialog v-model="formVisible" :title="editId ? '修改风险点 / 重新评估' : '新增风险点'" width="760px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="编号" prop="pointCode"><el-input v-model="form.pointCode" placeholder="如 RP-008" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="风险点名称" prop="pointName"><el-input v-model="form.pointName" maxlength="50" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="类型" prop="pointType">
              <el-select v-model="form.pointType" style="width: 100%"><el-option v-for="t in RISK_POINT_TYPES" :key="t" :label="t" :value="t" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16"><el-form-item label="位置" prop="location"><el-input v-model="form.location" maxlength="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="责任人" prop="responsibleId">
              <el-select v-model="form.responsibleId" filterable style="width: 100%">
                <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id"><span>{{ u.nickname }}</span><span class="opt-dept">{{ u.deptName }}</span></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="受限空间">
              <el-select v-model="form.spaceId" clearable filterable placeholder="可选" style="width: 100%">
                <el-option v-for="s in spaces" :key="s.id" :label="`${s.spaceCode} ${s.spaceName}`" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="危险有害因素"><el-input v-model="form.hazardFactors" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="可能导致"><el-input v-model="form.possibleAccidents" maxlength="200" placeholder="如：中毒窒息、火灾爆炸" /></el-form-item>

        <el-form-item label="LEC 评估" required>
          <div class="lec-box">
            <div class="lec-row">
              <span class="lec-k">L 可能性</span>
              <el-select v-model="form.lecL" placeholder="选择" style="flex: 1"><el-option v-for="o in lec.l" :key="o.value" :label="`${num(o.value)} — ${o.label}`" :value="o.value" /></el-select>
            </div>
            <div class="lec-row">
              <span class="lec-k">E 暴露频繁</span>
              <el-select v-model="form.lecE" placeholder="选择" style="flex: 1"><el-option v-for="o in lec.e" :key="o.value" :label="`${num(o.value)} — ${o.label}`" :value="o.value" /></el-select>
            </div>
            <div class="lec-row">
              <span class="lec-k">C 后果</span>
              <el-select v-model="form.lecC" placeholder="选择" style="flex: 1"><el-option v-for="o in lec.c" :key="o.value" :label="`${num(o.value)} — ${o.label}`" :value="o.value" /></el-select>
            </div>
            <div v-if="evaluated" class="lec-result" :style="{ borderColor: RISK_GRADES[evaluated.level].color }">
              <div>D = {{ num(form.lecL) }} × {{ num(form.lecE) }} × {{ num(form.lecC) }} = <b class="d">{{ num(evaluated.d) }}</b></div>
              <RiskBadge :level="evaluated.level" />
              <span class="muted">{{ RISK_GRADES[evaluated.level].control }}管控，巡检周期最长 {{ maxCycle }} 天</span>
            </div>
            <div class="muted lec-rule">分级：D &gt; 320 重大（红） · 160~320 较大（橙） · 70~160 一般（黄） · ≤ 70 低（蓝）</div>
          </div>
        </el-form-item>
        <el-form-item label="管控措施" prop="controlMeasures">
          <el-input v-model="form.controlMeasures" type="textarea" :rows="5" maxlength="2000"
                    placeholder="一行一条，巡检时逐条确认。例如：&#10;1. 进出物料管线加装盲板并挂牌&#10;2. 作业前 30 分钟内气体检测合格" />
        </el-form-item>
        <el-form-item label="巡检周期">
          <el-input-number v-model="form.checkCycleDays" :min="1" :max="maxCycle || 365" /> <span class="muted cycle-tip">天（风险越高，巡检越频繁）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  changeRiskStatus, createRisk, pageRisks, riskDetail, riskLecOptions, riskOverview, spaceOptions, updateRisk, userOptions
} from '@/api'
import { CHECK_STATES, RISK_GRADES, RISK_POINT_TYPES } from '@/utils/dict'
import { downloadFile } from '@/utils/download'
import RiskBadge from './RiskBadge.vue'

const store = useUserStore()
const router = useRouter()
const canEdit = computed(() => store.hasPerm('risk:point:edit'))
const tab = ref('list')
const query = reactive({ current: 1, size: 10, keyword: '', riskLevel: '', pointType: '', overdue: false })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const overview = ref(null)
const exporting = ref(false)
const num = (v) => (v == null ? '' : Number(v) % 1 === 0 ? Number(v) : Number(v))

async function load() {
  loading.value = true
  try {
    const data = await pageRisks({ ...query, overdue: query.overdue || undefined })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
async function loadOverview() {
  overview.value = await riskOverview()
}
function search() {
  query.current = 1
  load()
}
function filterLevel(k) {
  query.riskLevel = query.riskLevel === k ? '' : k
  tab.value = 'list'
  search()
}
function filterOverdue() {
  query.overdue = !query.overdue
  tab.value = 'list'
  search()
}
async function doExport() {
  exporting.value = true
  try {
    await downloadFile('/safety/risk/export', { ...query, overdue: query.overdue || undefined }, '风险分级管控清单.xlsx')
  } finally {
    exporting.value = false
  }
}
async function toggle(row) {
  const status = row.status === 1 ? 0 : 1
  await changeRiskStatus(row.id, status)
  ElMessage.success(status ? '已启用' : '已停用，不再提醒巡检')
  load()
  loadOverview()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref(null)
async function openDetail(row) {
  detail.value = await riskDetail(row.id)
  detailVisible.value = true
}
function failedItems(c) {
  try { return JSON.parse(c.items || '[]').filter((i) => !i.ok) } catch { return [] }
}
function goHazard(id) {
  router.push({ path: '/safety/hazard', query: { detail: id } })
}

// ---------- 新增 / 修改 ----------
const formVisible = ref(false)
const formRef = ref()
const editId = ref(null)
const saving = ref(false)
const users = ref([])
const spaces = ref([])
const lec = ref({ l: [], e: [], c: [], levels: [] })
const form = reactive({})
const rules = {
  pointCode: [{ required: true, message: '请填写编号', trigger: 'blur' }],
  pointName: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  pointType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  location: [{ required: true, message: '请填写位置', trigger: 'blur' }],
  responsibleId: [{ required: true, message: '请选择责任人', trigger: 'change' }],
  controlMeasures: [{ required: true, message: '请填写管控措施', trigger: 'blur' }]
}
// 和后端 LecEvaluator 同样的分级规则，前端实时预览
const evaluated = computed(() => {
  if (form.lecL == null || form.lecE == null || form.lecC == null) return null
  const d = Math.round(form.lecL * form.lecE * form.lecC * 10) / 10
  const level = d > 320 ? 'MAJOR' : d > 160 ? 'HIGH' : d > 70 ? 'GENERAL' : 'LOW'
  return { d, level }
})
const maxCycle = computed(() => lec.value.levels.find((l) => l.level === evaluated.value?.level)?.defaultCycleDays)
watch(() => evaluated.value?.level, (lv, old) => {
  // 等级变化时，把巡检周期调整为该等级的默认值
  if (lv && lv !== old && (form.checkCycleDays == null || form.checkCycleDays > maxCycle.value || old)) form.checkCycleDays = maxCycle.value
})
async function openForm(row) {
  editId.value = row?.id || null
  const [l, u, s] = await Promise.all([riskLecOptions(), userOptions(), spaceOptions()])
  lec.value = l
  users.value = u
  spaces.value = s
  if (row) {
    const p = (await riskDetail(row.id)).point
    Object.assign(form, { pointCode: p.pointCode, pointName: p.pointName, pointType: p.pointType, location: p.location,
      responsibleId: p.responsibleId, spaceId: p.spaceId, hazardFactors: p.hazardFactors, possibleAccidents: p.possibleAccidents,
      lecL: Number(p.lecL), lecE: Number(p.lecE), lecC: Number(p.lecC), controlMeasures: p.controlMeasures, checkCycleDays: p.checkCycleDays })
  } else {
    Object.assign(form, { pointCode: '', pointName: '', pointType: '设备设施', location: '', responsibleId: null, spaceId: null,
      hazardFactors: '', possibleAccidents: '', lecL: null, lecE: null, lecC: null, controlMeasures: '', checkCycleDays: null })
  }
  formVisible.value = true
}
async function save() {
  await formRef.value.validate()
  if (!evaluated.value) {
    ElMessage.warning('请完成 LEC 评估')
    return
  }
  saving.value = true
  try {
    const data = { ...form }
    if (editId.value) await updateRisk(editId.value, data)
    else await createRisk(data)
    ElMessage.success(`已保存：${RISK_GRADES[evaluated.value.level].label}`)
    formVisible.value = false
    load()
    loadOverview()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadOverview()
})
</script>

<style scoped>
.kpis { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 16px; }
.kpi { background: #fff; border-radius: 8px; padding: 12px 16px; cursor: pointer; border: 2px solid transparent; transition: border-color 0.15s; }
.kpi:hover { border-color: #dcdfe6; }
.kpi.active { border-color: #1f6feb; }
.kpi.grade { border-top: 4px solid var(--c); }
.kpi.grade.active { border-color: var(--c); }
.kpi.warn .value { color: #f56c6c; }
.kpi .label { color: #606266; font-size: 13px; display: flex; align-items: center; gap: 6px; }
.kpi .label i { width: 10px; height: 10px; border-radius: 2px; background: var(--c); }
.kpi .value { font-size: 26px; font-weight: 600; margin: 4px 0 2px; }
.kpi .sub { color: #909399; font-size: 12px; }
.lec { font-size: 13px; color: #606266; }
.small { font-size: 13px; }
.muted { color: #909399; font-size: 12px; }
.legend { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 12px; font-size: 13px; color: #606266; }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend i { width: 14px; height: 14px; border-radius: 3px; display: inline-block; }
.legend i.overdue-mark { border: 2px dashed #303133; }
.map { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 14px; }
.area { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.area-title { font-weight: 600; margin-bottom: 10px; }
.tiles { display: flex; flex-wrap: wrap; gap: 8px; }
.tile { width: 132px; border-radius: 6px; padding: 8px 10px; cursor: pointer; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1); transition: transform 0.1s; }
.tile:hover { transform: translateY(-2px); }
.tile.overdue { outline: 2px dashed #303133; outline-offset: 2px; }
.tile-code { font-size: 12px; opacity: 0.9; display: flex; justify-content: space-between; }
.tile-flag { background: rgba(0, 0, 0, 0.35); color: #fff; border-radius: 3px; padding: 0 4px; font-size: 11px; }
.tile-name { font-weight: 600; font-size: 13px; margin-top: 2px; line-height: 1.4; }
.d-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.section { font-weight: 600; margin: 16px 0 8px; }
.measures { margin: 0; padding-left: 20px; line-height: 1.9; }
.hz { margin-left: 8px; }
.failed { color: #f56c6c; font-size: 13px; margin-top: 2px; }
.opt-dept { float: right; color: #909399; font-size: 12px; }
.lec-box { width: 100%; background: #f8f9fb; border-radius: 8px; padding: 10px 12px; }
.lec-row { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.lec-k { width: 76px; color: #606266; font-size: 13px; }
.lec-result { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; background: #fff; border-left: 4px solid; border-radius: 4px; padding: 8px 12px; }
.lec-result .d { font-size: 20px; }
.lec-rule { margin-top: 6px; }
.cycle-tip { margin-left: 8px; }
@media (max-width: 1100px) { .kpis { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 600px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
</style>
