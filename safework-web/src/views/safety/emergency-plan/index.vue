<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="应急预案" name="plan">
        <div v-if="warnCount" class="warn-bar">
          <el-icon><WarningFilled /></el-icon>
          {{ warnCount }} 份预案从未演练或演练逾期（现场处置方案每半年至少一次，其他预案每年至少一次）
        </div>
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="编号 / 名称" clearable style="width: 200px" @keyup.enter="loadPlans" @clear="loadPlans" />
          <el-button icon="Search" @click="loadPlans">查询</el-button>
          <div class="spacer" />
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="openPlan()">新增预案</el-button>
        </div>
        <el-table v-loading="loading" :data="plans" stripe>
          <el-table-column prop="planCode" label="编号" width="120" />
          <el-table-column prop="title" label="预案名称" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="130"><template #default="{ row }">{{ PLAN_TYPES[row.planType] }}</template></el-table-column>
          <el-table-column label="事故类型" width="110"><template #default="{ row }">{{ ACCIDENT_TYPES[row.accidentType] }}</template></el-table-column>
          <el-table-column prop="teamNames" label="应急小组" min-width="150" show-overflow-tooltip />
          <el-table-column label="上次演练" width="110"><template #default="{ row }">{{ (row.lastDrillTime || '-').slice(0, 10) }}</template></el-table-column>
          <el-table-column label="演练状态" width="170">
            <template #default="{ row }">
              <template v-if="row.status === 1">
                <el-tag size="small" :type="DRILL_STATES[row.drillState]?.type">{{ DRILL_STATES[row.drillState]?.label }}</el-tag>
                <div v-if="row.nextDrillDue" class="due">应在 {{ row.nextDrillDue }} 前</div>
                <el-tooltip v-if="row.reviseNote" :content="'演练评估意见：' + row.reviseNote"><el-tag size="small" type="warning" class="revise">待修订</el-tag></el-tooltip>
              </template>
              <el-tag v-else size="small" type="info">已停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewPlan(row)">查看</el-button>
              <template v-if="canEdit">
                <el-button link type="primary" @click="openPlan(row)">编辑</el-button>
                <el-button v-if="row.status === 1" link type="primary" @click="openDrill(row)">安排演练</el-button>
                <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="应急演练" name="drill">
        <div class="toolbar">
          <el-select v-model="drillPlanId" clearable filterable placeholder="按预案筛选" style="width: 240px" @change="loadDrills">
            <el-option v-for="p in plans" :key="p.id" :label="`${p.planCode} ${p.title}`" :value="p.id" />
          </el-select>
          <el-select v-model="drillStatus" clearable placeholder="状态" style="width: 110px" @change="loadDrills">
            <el-option v-for="(v, k) in DRILL_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
          <div class="spacer" />
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="openDrill()">安排演练</el-button>
        </div>
        <el-table v-loading="drillLoading" :data="drills" stripe class="clickable" @row-click="(r) => openDetail(r.drill.id)">
          <el-table-column label="演练名称" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.drill.title }}</template></el-table-column>
          <el-table-column label="预案" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.drill.planTitle }}</template></el-table-column>
          <el-table-column label="形式" width="90"><template #default="{ row }">{{ DRILL_TYPES[row.drill.drillType] }}</template></el-table-column>
          <el-table-column label="时间" width="140"><template #default="{ row }">{{ (row.drill.actualTime || row.drill.planTime || '').slice(0, 16) }}</template></el-table-column>
          <el-table-column label="参演" width="100">
            <template #default="{ row }">
              <template v-if="row.drill.participantCount">{{ row.drill.participantCount }} 人</template>
              <template v-else-if="row.participants">签到 {{ row.signed }}/{{ row.participants }}</template>
              <template v-else>-</template>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="DRILL_STATUS[row.drill.status]?.type">{{ DRILL_STATUS[row.drill.status]?.label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="评估" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.drill.grade" size="small" :type="DRILL_GRADE_TYPES[row.drill.grade]">{{ row.drill.totalScore }} · {{ row.drill.grade }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="问题 / 已整改" width="110" align="center">
            <template #default="{ row }">{{ row.problems ? `${row.problems} / ${row.hazards}` : '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="年度演练计划" name="annual">
        <DrillAnnual ref="annualRef" :can-edit="canEdit" @open="openDetail" @plan="(id) => openDrill({ id })" />
      </el-tab-pane>
    </el-tabs>

    <!-- 查看预案 -->
    <el-drawer v-model="viewVisible" size="min(620px, 100vw)" :title="viewing ? `${viewing.planCode} ${viewing.title}` : ''">
      <template v-if="viewing">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类型">{{ PLAN_TYPES[viewing.planType] }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ ACCIDENT_TYPES[viewing.accidentType] }}</el-descriptions-item>
          <el-descriptions-item label="适用范围" :span="2">{{ viewing.scope || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应急小组" :span="2">{{ viewing.teamNames || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上次演练">{{ viewing.lastDrillTime || '从未演练' }}</el-descriptions-item>
          <el-descriptions-item label="最后修改">{{ viewing.updaterName || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>应急处置程序</h4>
        <div class="plan-content">{{ viewing.content }}</div>
      </template>
    </el-drawer>

    <!-- 新增 / 编辑预案 -->
    <el-dialog v-model="planVisible" :title="form.id ? '编辑预案' : '新增预案'" width="min(680px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :xs="24" :span="10"><el-form-item label="预案编号" prop="planCode"><el-input v-model="form.planCode" maxlength="30" placeholder="如 YA-XC-03" /></el-form-item></el-col>
          <el-col :xs="24" :span="14"><el-form-item label="预案名称" prop="title"><el-input v-model="form.title" maxlength="60" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :span="12">
            <el-form-item label="预案类型" prop="planType">
              <el-select v-model="form.planType" style="width: 100%"><el-option v-for="(v, k) in PLAN_TYPES" :key="k" :label="v" :value="k" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :span="12">
            <el-form-item label="事故类型" prop="accidentType">
              <el-select v-model="form.accidentType" style="width: 100%"><el-option v-for="(v, k) in ACCIDENT_TYPES" :key="k" :label="v" :value="k" /></el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="适用范围"><el-input v-model="form.scope" maxlength="200" placeholder="如：污水处理站、化粪池等受限空间作业" /></el-form-item>
        <el-form-item label="应急小组">
          <el-select v-model="form.teamUserIds" multiple filterable :multiple-limit="30" style="width: 100%" placeholder="启动响应和安排演练时会通知他们">
            <el-option v-for="u in users" :key="u.id" :label="u.deptName ? `${u.nickname}（${u.deptName}）` : u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置程序" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="5000" show-word-limit
                    placeholder="按步骤写，每步一行，如：&#10;1. 立即停止作业，撤离现场人员&#10;2. 拨打 120，报告安全员&#10;3. 救援人员佩戴空气呼吸器后方可进入，严禁盲目施救" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </template>
    </el-dialog>

    <DrillForm v-model="drillVisible" :plans="plans" :data="drillEditing" :plan-id="drillForPlan" @saved="afterDrillSaved" />
    <DrillDetail v-model="detailVisible" :id="detailId" @changed="onDrillChanged" @edit="editDrill" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { emergencyDrills, emergencyPlan, emergencyPlans, emergencyPlanStatus, saveEmergencyPlan, userOptions } from '@/api'
import { ACCIDENT_TYPES, DRILL_GRADE_TYPES, DRILL_STATES, DRILL_STATUS, DRILL_TYPES, PLAN_TYPES } from '@/utils/dict'
import DrillAnnual from './DrillAnnual.vue'
import DrillDetail from './DrillDetail.vue'
import DrillForm from './DrillForm.vue'

const route = useRoute()
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('emergency:plan:edit'))
const tab = ref(['drill', 'annual'].includes(route.query.tab) ? route.query.tab : 'plan')
const keyword = ref('')
const loading = ref(false)
const plans = ref([])
const warnCount = computed(() => plans.value.filter((p) => p.status === 1 && ['NEVER', 'OVERDUE'].includes(p.drillState)).length)

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await emergencyPlans({ keyword: keyword.value || undefined })
  } finally {
    loading.value = false
  }
}

// ---------------- 查看 / 编辑预案 ----------------
const viewVisible = ref(false)
const viewing = ref(null)
async function viewPlan(row) {
  viewing.value = row
  viewVisible.value = true
  viewing.value = await emergencyPlan(row.id)
}

const planVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const users = ref([])
const form = reactive({})
const rules = {
  planCode: [{ required: true, message: '请填写预案编号', trigger: 'blur' }],
  title: [{ required: true, message: '请填写预案名称', trigger: 'blur' }],
  planType: [{ required: true, message: '请选择预案类型' }],
  accidentType: [{ required: true, message: '请选择适用的事故类型' }],
  content: [{ required: true, message: '请填写应急处置程序', trigger: 'blur' }]
}
async function openPlan(row) {
  const full = row ? await emergencyPlan(row.id) : null
  Object.assign(form, {
    id: full?.id || null,
    planCode: full?.planCode || '',
    title: full?.title || '',
    planType: full?.planType || 'ONSITE',
    accidentType: full?.accidentType || '',
    scope: full?.scope || '',
    content: full?.content || '',
    // 后端存的是逗号分隔的字符串，id 都按字符串处理（Long 序列化成字符串）
    teamUserIds: full?.teamUserIds ? full.teamUserIds.split(',').filter(Boolean) : []
  })
  planVisible.value = true
  formRef.value?.clearValidate()
  if (!users.value.length) users.value = (await userOptions()).map((u) => ({ ...u, id: String(u.id) }))
}
async function savePlan() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    await saveEmergencyPlan(id, data)
    ElMessage.success('已保存')
    planVisible.value = false
    loadPlans()
  } finally {
    saving.value = false
  }
}
async function toggle(row) {
  const to = row.status === 1 ? 0 : 1
  if (to === 0) await ElMessageBox.confirm(`停用后，发生事故时不能再启动《${row.title}》，确定停用？`, '提示', { type: 'warning' })
  await emergencyPlanStatus(row.id, to)
  ElMessage.success(to ? '已启用' : '已停用')
  loadPlans()
}

// ---------------- 演练 ----------------
const drillLoading = ref(false)
const drills = ref([])
const drillPlanId = ref(null)
const drillStatus = ref('')
const annualRef = ref()
async function loadDrills() {
  drillLoading.value = true
  try {
    drills.value = await emergencyDrills({ planId: drillPlanId.value || undefined, status: drillStatus.value || undefined })
  } finally {
    drillLoading.value = false
  }
}
function onTab(name) {
  if (name === 'drill') loadDrills()
  if (name === 'annual') nextTick(() => annualRef.value?.load())
}

const drillVisible = ref(false)
const drillEditing = ref(null)
const drillForPlan = ref(null)
function openDrill(row) {
  drillEditing.value = null
  drillForPlan.value = row?.id || null
  drillVisible.value = true
}
function editDrill(detail) {
  drillEditing.value = detail
  drillForPlan.value = null
  detailVisible.value = false
  drillVisible.value = true
}
function afterDrillSaved() {
  if (tab.value === 'plan') tab.value = 'drill'
  refreshDrills()
}
function refreshDrills() {
  loadDrills()
  loadPlans()
  if (tab.value === 'annual') annualRef.value?.load()
}
function onDrillChanged() {
  refreshDrills()
}

const detailVisible = ref(false)
const detailId = ref(null)
function openDetail(id) {
  detailId.value = id
  detailVisible.value = true
}

onMounted(() => {
  loadPlans()
  onTab(tab.value)
})
watch(() => route.query.tab, (t) => {
  if (t === 'drill') {
    tab.value = 'drill'
    loadDrills()
  }
})
</script>

<style scoped>
.warn-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; padding: 10px 14px; border-radius: 6px; background: #fdf6ec; color: #b88230; font-size: 14px; }
.due { color: #999; font-size: 12px; }
.revise { margin-top: 4px; }
.clickable :deep(tr) { cursor: pointer; }
h4 { margin: 20px 0 10px; }
.plan-content, .pre { white-space: pre-wrap; word-break: break-all; }
.plan-content { padding: 12px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; font-size: 14px; line-height: 1.8; }
</style>
