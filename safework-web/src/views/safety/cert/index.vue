<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane :label="canEdit ? '证书台账' : '我的证书'" name="list">
        <div v-if="overview" class="stats">
          <div class="stat" :class="{ on: !query.state }" @click="filterState('')"><b>{{ overview.total }}</b><span>证书（{{ overview.holders }} 人）</span></div>
          <div class="stat warn" :class="{ on: query.state === 'EXPIRING' }" @click="filterState('EXPIRING')"><b>{{ overview.expiring }}</b><span>30 天内到期</span></div>
          <div class="stat danger" :class="{ on: query.state === 'EXPIRED' }" @click="filterState('EXPIRED')"><b>{{ overview.expired }}</b><span>已过期 / 复审逾期</span></div>
        </div>

        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="姓名 / 证书编号" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
          <el-select v-model="query.certType" placeholder="证书类型" clearable style="width: 230px" @change="search">
            <el-option v-for="(v, k) in CERT_TYPES" :key="k" :label="v" :value="k" />
          </el-select>
          <el-select v-model="query.state" placeholder="状态" clearable style="width: 110px" @change="search">
            <el-option v-for="(v, k) in CERT_STATES" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button icon="Search" @click="search">查询</el-button>
          <div class="spacer" />
          <el-button icon="Download" :loading="exporting" @click="doExport">导出</el-button>
          <el-button v-if="canEdit" type="primary" icon="Plus" @click="openForm()">登记证书</el-button>
        </div>

        <el-table v-loading="loading" :data="list" stripe @row-click="openDetail">
          <el-table-column prop="userName" label="持证人" min-width="140" />
          <el-table-column prop="deptName" label="部门" width="110" />
          <el-table-column label="证书类型" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ CERT_TYPES[row.certType] }}</template></el-table-column>
          <el-table-column prop="certNo" label="证书编号" min-width="170" show-overflow-tooltip />
          <el-table-column label="有效期至" width="110"><template #default="{ row }">{{ row.expireDate || '长期' }}</template></el-table-column>
          <el-table-column label="下次复审" width="110"><template #default="{ row }">{{ row.reviewDate || '-' }}</template></el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="CERT_STATES[row.state]?.type">{{ CERT_STATES[row.state]?.label }}</el-tag>
              <span v-if="row.daysLeft != null && row.state !== 'VALID'" class="days" :class="row.state">{{ daysText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openForm(row)">编辑</el-button>
              <el-button link type="danger" @click.stop="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="开票持证要求" name="requirement">
        <el-alert type="info" :closable="false" show-icon class="req-tip"
                  title="作业票提交审批、开始作业时，系统检查作业负责人和监护人是否持有下面勾选的证书，并且证书在作业结束前一直有效，不符合的直接拦截。都不勾表示这类作业不检查持证。" />
        <el-table :data="reqRows" border>
          <el-table-column label="作业类型" width="130"><template #default="{ row }">{{ TICKET_TYPES[row.ticketType].label }}</template></el-table-column>
          <el-table-column label="人员" width="110"><template #default="{ row }">{{ CERT_HOLDER_ROLES[row.holderRole] }}</template></el-table-column>
          <el-table-column label="必须持有的证书">
            <template #default="{ row }">
              <el-checkbox-group v-model="row.certTypes" :disabled="!canEdit">
                <el-checkbox v-for="(v, k) in REQ_CERTS" :key="k" :value="k">{{ v }}</el-checkbox>
              </el-checkbox-group>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="canEdit" class="req-footer">
          <el-button type="primary" :loading="savingReq" @click="saveReq">保存持证要求</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 证书详情 + 扫描件 -->
    <el-drawer v-model="detailVisible" size="min(520px, 100vw)" :title="detail ? `${detail.userName} · ${CERT_TYPES[detail.certType]}` : ''">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="持证人">{{ detail.userName }}<span v-if="detail.deptName" class="muted">（{{ detail.deptName }}）</span></el-descriptions-item>
          <el-descriptions-item label="证书类型">{{ CERT_TYPES[detail.certType] }}</el-descriptions-item>
          <el-descriptions-item label="证书编号">{{ detail.certNo }}</el-descriptions-item>
          <el-descriptions-item label="发证机关">{{ detail.issuer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发证日期">{{ detail.issueDate }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ detail.expireDate || '长期' }}</el-descriptions-item>
          <el-descriptions-item label="下次复审">{{ detail.reviewDate || '不需要复审' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="CERT_STATES[detail.state]?.type">{{ CERT_STATES[detail.state]?.label }}</el-tag>
            <span v-if="detail.daysLeft != null" class="days" :class="detail.state">{{ daysText(detail) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后修改">{{ detail.updaterName }}　{{ detail.updateTime }}</el-descriptions-item>
        </el-descriptions>
        <AttachmentPanel :key="detail.id" biz-type="CERT" :biz-id="detail.id" tag="SCAN" title="证书扫描件" image-only
                         empty-text="安全员可以上传证书正反面照片，检查时直接出示" />
      </template>
    </el-drawer>

    <!-- 登记 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '修改证书' : '登记证书'" width="min(560px, 94vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="持证人" prop="userId">
          <el-select v-model="form.userId" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.deptName ? `${u.nickname}（${u.deptName}）` : u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="证书类型" prop="certType">
          <el-select v-model="form.certType" style="width: 100%" @change="autoDates">
            <el-option v-for="(v, k) in CERT_TYPES" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="证书编号" prop="certNo"><el-input v-model="form.certNo" maxlength="50" /></el-form-item>
        <el-form-item label="发证机关"><el-input v-model="form.issuer" maxlength="60" placeholder="如：安徽省应急管理厅" /></el-form-item>
        <el-form-item label="发证日期" prop="issueDate">
          <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 100%" @change="autoDates" />
        </el-form-item>
        <el-form-item label="有效期至">
          <div class="date-row">
            <el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" :disabled="longTerm" placeholder="有效期截止日" style="flex: 1" />
            <el-checkbox v-model="longTerm" @change="(v) => v && (form.expireDate = null)">长期</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="下次复审">
          <el-date-picker v-model="form.reviewDate" type="date" value-format="YYYY-MM-DD" clearable placeholder="不需要复审就不填" style="width: 100%" />
          <div class="hint">特种作业操作证有效期 6 年、每 3 年复审一次；选择证书类型和发证日期后自动填写，可以修改</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { certDetail, certOverview, certPage, certRequirements, deleteCert, saveCert, saveCertRequirements, userOptions } from '@/api'
import { CERT_HOLDER_ROLES, CERT_STATES, CERT_TYPES, TICKET_TYPES } from '@/utils/dict'
import { downloadFile } from '@/utils/download'
import AttachmentPanel from '@/components/AttachmentPanel.vue'

const store = useUserStore()
const canEdit = computed(() => store.hasPerm('cert:edit'))
const tab = ref('list')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const overview = ref(null)
const query = reactive({ current: 1, size: 10, keyword: '', certType: '', state: '' })

const daysText = (c) => (c.daysLeft < 0 ? `过期 ${-c.daysLeft} 天` : c.daysLeft === 0 ? '今天到期' : `剩 ${c.daysLeft} 天`)

async function load() {
  loading.value = true
  try {
    const params = Object.fromEntries(Object.entries(query).filter(([, v]) => v !== '' && v != null))
    const page = await certPage(params)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}
function search() {
  query.current = 1
  load()
}
function reload() {
  load()
  certOverview().then((v) => { overview.value = v })
}
function filterState(s) {
  query.state = s
  search()
}

const exporting = ref(false)
async function doExport() {
  exporting.value = true
  try {
    const { current, size, ...params } = query
    await downloadFile('/safety/cert/export', params, '人员资质台账.xlsx')
  } finally {
    exporting.value = false
  }
}

// ---------------- 详情 ----------------
const detailVisible = ref(false)
const detail = ref(null)
async function openDetail(row) {
  detail.value = row
  detailVisible.value = true
  detail.value = await certDetail(row.id)
}

// ---------------- 登记 / 编辑 ----------------
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref()
const users = ref([])
const longTerm = ref(false)
const form = reactive({})
const rules = {
  userId: [{ required: true, message: '请选择持证人' }],
  certType: [{ required: true, message: '请选择证书类型' }],
  certNo: [{ required: true, message: '请填写证书编号', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请填写发证日期' }]
}
// 特种作业操作证：有效期 6 年，每 3 年复审；其他培训合格证一般 3 年
const SPECIAL = ['WELDING', 'HEIGHT', 'ELECTRICIAN', 'HAZCHEM']
const addYears = (d, n) => {
  const [y, m, day] = d.split('-').map(Number)
  const t = new Date(y + n, m - 1, day)
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
}
function autoDates() {
  if (form.id || !form.issueDate || !form.certType || form.certType === 'OTHER') return
  const special = SPECIAL.includes(form.certType)
  longTerm.value = false
  form.expireDate = addYears(form.issueDate, special ? 6 : 3)
  form.reviewDate = special ? addYears(form.issueDate, 3) : null
}
async function openForm(row) {
  Object.assign(form, {
    id: row?.id || null, userId: row?.userId || null, certType: row?.certType || '', certNo: row?.certNo || '', issuer: row?.issuer || '',
    issueDate: row?.issueDate || '', expireDate: row?.expireDate || null, reviewDate: row?.reviewDate || null, remark: row?.remark || ''
  })
  longTerm.value = !!row && !row.expireDate
  formVisible.value = true
  formRef.value?.clearValidate()
  if (!users.value.length) users.value = await userOptions()
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const { id, ...data } = form
    if (longTerm.value) data.expireDate = null
    await saveCert(id, data)
    ElMessage.success('已保存')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}
async function remove(row) {
  await ElMessageBox.confirm(`删除 ${row.userName} 的${CERT_TYPES[row.certType]}（${row.certNo}）？`, '提示', { type: 'warning' })
  await deleteCert(row.id)
  ElMessage.success('已删除')
  reload()
}

// ---------------- 开票持证要求 ----------------
const REQ_CERTS = Object.fromEntries(Object.entries(CERT_TYPES).filter(([k]) => k !== 'OTHER'))
const reqRows = ref([])
const savingReq = ref(false)
async function loadReq() {
  const reqs = await certRequirements()
  reqRows.value = Object.keys(TICKET_TYPES).flatMap((ticketType) => Object.keys(CERT_HOLDER_ROLES).map((holderRole) => ({
    ticketType,
    holderRole,
    certTypes: reqs.filter((r) => r.ticketType === ticketType && r.holderRole === holderRole).map((r) => r.certType)
  })))
}
async function saveReq() {
  savingReq.value = true
  try {
    await saveCertRequirements(reqRows.value.flatMap((r) => r.certTypes.map((certType) => ({ ticketType: r.ticketType, holderRole: r.holderRole, certType }))))
    ElMessage.success('已保存，新提交的作业票按新要求检查')
    loadReq()
  } finally {
    savingReq.value = false
  }
}

onMounted(() => {
  reload()
  loadReq()
})
</script>

<style scoped>
.stats { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.stat { flex: 1; min-width: 140px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 8px; cursor: pointer; display: flex; align-items: baseline; gap: 8px; }
.stat b { font-size: 24px; }
.stat span { color: #888; font-size: 13px; }
.stat.warn b { color: #e6a23c; }
.stat.danger b { color: #f56c6c; }
.stat.on { border-color: #409eff; background: #f5faff; }
:deep(.el-table__row) { cursor: pointer; }
.days { margin-left: 6px; font-size: 12px; color: #999; }
.days.EXPIRING { color: #e6a23c; }
.days.EXPIRED { color: #f56c6c; }
.muted { color: #999; }
.date-row { display: flex; gap: 12px; align-items: center; width: 100%; }
.hint { width: 100%; color: #999; font-size: 12px; line-height: 1.6; }
.req-tip { margin-bottom: 12px; }
.req-footer { margin-top: 16px; text-align: right; }
:deep(.el-checkbox) { margin-right: 18px; }
</style>
