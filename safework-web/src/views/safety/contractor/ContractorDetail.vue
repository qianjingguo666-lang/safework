<template>
  <el-drawer v-model="visible" size="min(760px, 100vw)" :title="c ? c.name : '承包商'">
    <div v-if="c" v-loading="loading">
      <div class="head">
        <el-tag size="large" :type="CONTRACTOR_STATUS[c.status]?.type" :effect="c.status === 'BLACKLISTED' ? 'dark' : 'light'">{{ CONTRACTOR_STATUS[c.status]?.label }}</el-tag>
        <span class="score">安全评分 <b :class="scoreClass(c.score)">{{ c.score }}</b><small>（近 12 个月，低于 60 分自动拉黑）</small></span>
      </div>
      <el-alert v-if="c.statusComment && c.status !== 'APPROVED'" :type="c.status === 'BLACKLISTED' ? 'error' : 'warning'" :closable="false" class="block"
                :title="`${c.reviewerName || ''}：${c.statusComment}`" />
      <el-alert v-for="w in c.warnings" :key="w" type="warning" :closable="false" show-icon class="block" :title="w" />

      <div v-if="actions.length || canEdit" class="actions">
        <el-button v-for="a in actions" :key="a" :type="CONTRACTOR_ACTIONS[a].type" @click="doAction(a)">{{ CONTRACTOR_ACTIONS[a].label }}</el-button>
        <el-button v-if="canEdit" icon="Edit" @click="emit('edit', c)">修改信息</el-button>
      </div>

      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item label="统一社会信用代码" :span="2">{{ c.creditCode }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ c.contactName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ c.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="业务范围" :span="2">{{ c.businessScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资质证书有效期">{{ c.licenseExpire }}</el-descriptions-item>
        <el-descriptions-item label="责任险到期">{{ c.insuranceExpire }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ c.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="tab" class="block">
        <el-tab-pane :label="`作业人员（${workers.filter((w) => w.status === 1).length} 人在场）`" name="worker">
          <div v-if="canEdit && c.status !== 'BLACKLISTED'" class="tab-tool"><el-button size="small" type="primary" icon="Plus" @click="openWorker()">登记人员</el-button></div>
          <el-table :data="workers" size="small" border>
            <el-table-column label="姓名" width="110">
              <template #default="{ row }">{{ row.name }}<el-tag v-if="row.status !== 1" size="small" type="info" class="ml">离场</el-tag></template>
            </el-table-column>
            <el-table-column prop="jobType" label="工种" width="80" />
            <el-table-column label="入厂培训" width="120">
              <template #default="{ row }">{{ row.trainingDate || '未培训' }}<div v-if="row.trainingExpire" class="muted">至 {{ row.trainingExpire }}</div></template>
            </el-table-column>
            <el-table-column label="特种作业证" min-width="150">
              <template #default="{ row }">
                <template v-if="row.certType">{{ CERT_TYPES[row.certType] }}<div class="muted">{{ row.certNo }}，至 {{ row.certExpire }}</div></template>
                <span v-else class="muted">无</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" min-width="130">
              <template #default="{ row }">
                <el-tag v-if="!row.problems.length" size="small" type="success">可以作业</el-tag>
                <div v-for="p in row.problems" :key="p" class="problem">{{ p }}</div>
              </template>
            </el-table-column>
            <el-table-column v-if="canEdit" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openWorker(row)">编辑</el-button>
                <el-button link size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggleWorker(row)">{{ row.status === 1 ? '离场' : '进场' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`违章记录（${violations.length}）`" name="violation">
          <div v-if="canEdit" class="tab-tool"><el-button size="small" type="danger" icon="Warning" @click="openViolation">记录违章</el-button></div>
          <el-empty v-if="!violations.length" description="没有违章记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item v-for="v in violations" :key="v.id" :timestamp="v.occurTime" placement="top" type="danger">
              <b>扣 {{ v.points }} 分</b>　{{ v.workerName || '单位' }}
              <div class="comment">{{ v.description }}</div>
              <div class="muted">记录人：{{ v.recorderName }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 人员 -->
    <el-dialog v-model="workerVisible" :title="worker.id ? '修改人员' : '登记人员'" width="min(520px, 94vw)" append-to-body>
      <el-form ref="workerRef" :model="worker" :rules="workerRules" label-width="100px">
        <el-form-item label="姓名" prop="name"><el-input v-model="worker.name" maxlength="20" /></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="worker.phone" maxlength="11" /></el-form-item>
        <el-form-item label="工种"><el-input v-model="worker.jobType" maxlength="20" placeholder="如：焊工、架子工、清洗工" /></el-form-item>
        <el-form-item label="入厂安全培训">
          <el-date-picker v-model="worker.trainingDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" placeholder="培训考核合格的日期" style="width: 100%" />
          <div class="hint">入厂安全培训有效期 12 个月，没培训或过期的人员不能参与作业</div>
        </el-form-item>
        <el-form-item label="特种作业证">
          <el-select v-model="worker.certType" clearable placeholder="没有就不选" style="width: 100%">
            <el-option v-for="(v, k) in CERT_TYPES" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <template v-if="worker.certType">
          <el-form-item label="证书编号" prop="certNo"><el-input v-model="worker.certNo" maxlength="50" /></el-form-item>
          <el-form-item label="有效期至" prop="certExpire"><el-date-picker v-model="worker.certExpire" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="workerVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="saveWorker">保存</el-button>
      </template>
    </el-dialog>

    <!-- 违章 -->
    <el-dialog v-model="violationVisible" title="记录违章" width="min(500px, 94vw)" append-to-body>
      <el-form ref="violationRef" :model="violation" :rules="violationRules" label-width="90px">
        <el-form-item label="违章人员">
          <el-select v-model="violation.workerId" clearable placeholder="不选表示单位整体违章" style="width: 100%">
            <el-option v-for="w in workers" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="违章情况" prop="description"><el-input v-model="violation.description" type="textarea" :rows="3" maxlength="300" placeholder="如：高处作业未系安全带" /></el-form-item>
        <el-form-item label="扣分" prop="points">
          <el-input-number v-model="violation.points" :min="1" :max="20" />
          <span class="muted ml">当前 {{ c?.score }} 分，扣完 {{ Math.max(0, (c?.score || 0) - (violation.points || 0)) }} 分</span>
        </el-form-item>
        <el-form-item label="违章时间" prop="occurTime">
          <el-date-picker v-model="violation.occurTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="(d) => d > new Date()" style="width: 100%" />
        </el-form-item>
      </el-form>
      <el-alert v-if="c?.status === 'APPROVED' && (c.score - (violation.points || 0)) < 60" type="error" :closable="false" show-icon
                title="扣分后低于 60 分，这家承包商会自动列入黑名单" />
      <template #footer>
        <el-button @click="violationVisible = false">取消</el-button>
        <el-button type="danger" :loading="busy" @click="saveViolation">确定扣分</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { addContractorViolation, contractorAction, contractorDetail, contractorWorkerStatus, saveContractorWorker } from '@/api'
import { CERT_TYPES, CONTRACTOR_ACTIONS, CONTRACTOR_STATUS } from '@/utils/dict'

const emit = defineEmits(['changed', 'edit'])
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('contractor:edit'))
const visible = ref(false)
const loading = ref(false)
const busy = ref(false)
const tab = ref('worker')
const c = ref(null)
const workers = ref([])
const violations = ref([])
const actions = ref([])
const scoreClass = (s) => (s < 60 ? 'danger' : s < 80 ? 'warn' : 'ok')

async function open(id) {
  visible.value = true
  loading.value = true
  try {
    const d = await contractorDetail(id)
    c.value = d.contractor
    workers.value = d.workers
    violations.value = d.violations
    actions.value = d.actions || []
  } finally {
    loading.value = false
  }
}
async function refresh() {
  emit('changed')
  await open(c.value.id)
}

async function doAction(a) {
  const cfg = CONTRACTOR_ACTIONS[a]
  let comment = ''
  if (cfg.comment) {
    const r = await ElMessageBox.prompt(`请填写${cfg.label}的原因`, cfg.label, {
      inputPattern: /\S/, inputErrorMessage: '原因不能为空', inputType: 'textarea'
    })
    comment = r.value
  } else {
    await ElMessageBox.confirm(`确定${cfg.label}？`, '提示', { type: 'warning' })
  }
  await contractorAction(c.value.id, a.toLowerCase(), comment)
  ElMessage.success(`已${cfg.label}`)
  refresh()
}

// ---------------- 人员 ----------------
const workerVisible = ref(false)
const workerRef = ref()
const worker = reactive({})
const workerRules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  certNo: [{ required: true, message: '请填写证书编号', trigger: 'blur' }],
  certExpire: [{ required: true, message: '请填写有效期' }]
}
function openWorker(w) {
  Object.assign(worker, {
    id: w?.id || null, name: w?.name || '', phone: w?.phone || '', jobType: w?.jobType || '', trainingDate: w?.trainingDate || null,
    certType: w?.certType || '', certNo: w?.certNo || '', certExpire: w?.certExpire || null
  })
  workerVisible.value = true
  workerRef.value?.clearValidate()
}
async function saveWorker() {
  await workerRef.value.validate()
  busy.value = true
  try {
    const { id, ...data } = worker
    if (!data.certType) {
      data.certType = null
      data.certNo = null
      data.certExpire = null
    }
    await saveContractorWorker(c.value.id, id, data)
    ElMessage.success('已保存')
    workerVisible.value = false
    refresh()
  } finally {
    busy.value = false
  }
}
async function toggleWorker(w) {
  const to = w.status === 1 ? 0 : 1
  await contractorWorkerStatus(c.value.id, w.id, to)
  ElMessage.success(to ? '已进场' : '已离场')
  refresh()
}

// ---------------- 违章 ----------------
const violationVisible = ref(false)
const violationRef = ref()
const violation = reactive({})
const violationRules = {
  description: [{ required: true, message: '请填写违章情况', trigger: 'blur' }],
  points: [{ required: true, message: '请填写扣分' }],
  occurTime: [{ required: true, message: '请填写违章时间' }]
}
const nowStr = () => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return d.toISOString().slice(0, 19).replace('T', ' ')
}
function openViolation() {
  Object.assign(violation, { workerId: null, description: '', points: 5, occurTime: nowStr() })
  violationVisible.value = true
  violationRef.value?.clearValidate()
}
async function saveViolation() {
  await violationRef.value.validate()
  busy.value = true
  try {
    const r = await addContractorViolation(c.value.id, violation)
    if (r.blacklisted) ElMessage.error(`已记录。安全评分 ${r.score} 分，已自动列入黑名单`)
    else ElMessage.success(`已记录，当前安全评分 ${r.score} 分`)
    violationVisible.value = false
    tab.value = 'violation'
    refresh()
  } finally {
    busy.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.score { font-size: 14px; color: #666; }
.score b { font-size: 22px; margin: 0 4px; }
.score small { color: #999; }
.ok { color: #67c23a; }
.warn { color: #e6a23c; }
.danger { color: #f56c6c; }
.block { margin-top: 12px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; padding: 12px; background: #f6f8fb; border-radius: 6px; }
.actions .el-button { margin-left: 0; }
.tab-tool { margin-bottom: 8px; text-align: right; }
.muted { color: #999; font-size: 12px; }
.ml { margin-left: 6px; }
.problem { color: #f56c6c; font-size: 12px; line-height: 1.5; }
.comment { color: #555; margin-top: 4px; }
.hint { width: 100%; color: #999; font-size: 12px; line-height: 1.6; }
</style>
