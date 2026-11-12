<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="mb"
      title="提前安排作业时间、班组和负责人，可关联作业票并记录实际开工、完工时间。作业计划不能代替作业票审批。" />
    <div class="toolbar">
      <el-select v-model="q.status" clearable placeholder="计划状态" style="width: 130px" @change="search">
        <el-option v-for="(label, key) in options.statuses" :key="key" :label="label" :value="key" />
      </el-select>
      <el-select v-model="q.ticketType" clearable placeholder="作业类型" style="width: 150px" @change="search">
        <el-option v-for="(label, key) in options.ticketTypes" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="q.keyword" clearable placeholder="计划编号 / 名称 / 地点" style="width: 230px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canManage" type="primary" icon="Plus" @click="edit(null)">新增作业计划</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="planNo" label="计划编号" width="170" />
      <el-table-column prop="title" label="作业名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="作业类型" width="120"><template #default="{ row }">{{ options.ticketTypes[row.ticketType] || row.ticketType }}</template></el-table-column>
      <el-table-column prop="location" label="地点" min-width="140" show-overflow-tooltip />
      <el-table-column prop="departmentName" label="班组" width="140" />
      <el-table-column prop="responsibleName" label="负责人" width="110" />
      <el-table-column label="计划时间" min-width="300"><template #default="{ row }">{{ row.plannedStartTime }} 至 {{ row.plannedEndTime }}</template></el-table-column>
      <el-table-column label="关联作业票" width="160"><template #default="{ row }">{{ row.ticketNo || '未关联' }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ options.statuses[row.status] || row.status }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage && row.status === 'PLANNED'" link type="primary" @click="edit(row)">编辑</el-button>
          <el-button v-if="canManage && row.status === 'PLANNED'" link type="success" @click="action(row, 'start')">开始</el-button>
          <el-button v-if="canManage && row.status === 'IN_PROGRESS'" link type="success" @click="action(row, 'complete')">完成</el-button>
          <el-button v-if="canManage && row.status === 'PLANNED'" link type="danger" @click="action(row, 'cancel')">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="q.current" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑作业计划' : '新增作业计划'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="作业名称" prop="title"><el-input v-model="form.title" maxlength="100" /></el-form-item>
        <el-form-item label="作业类型" prop="ticketType">
          <el-select v-model="form.ticketType" placeholder="请选择" style="width: 100%" @change="form.ticketId = null">
            <el-option v-for="(label, key) in options.ticketTypes" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="作业地点" prop="location"><el-input v-model="form.location" maxlength="100" /></el-form-item>
        <el-form-item label="作业内容" prop="workContent"><el-input v-model="form.workContent" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="部门 / 班组"><el-input v-model="form.departmentName" maxlength="60" /></el-form-item>
        <el-form-item label="作业负责人" prop="responsibleId">
          <el-select v-model="form.responsibleId" filterable placeholder="请选择负责人" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname + '（' + u.username + '）'" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间" prop="timeRange">
          <el-date-picker v-model="form.timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" :popper-options="PICKER_POPPER" />
        </el-form-item>
        <el-form-item label="关联作业票">
          <el-select v-model="form.ticketId" clearable filterable placeholder="可暂不关联" style="width: 100%">
            <el-option v-for="t in tickets" :key="t.id" :label="t.ticketNo + ' · ' + t.title" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { userOptions, pageTickets, jobPlanOptions, jobPlanPage, saveJobPlan, updateJobPlan, jobPlanAction } from '@/api'
import { PICKER_POPPER } from '@/utils/picker'

const store = useUserStore()
const canManage = computed(() => store.hasPerm('job:plan:manage'))
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const formRef = ref()
const rows = ref([])
const total = ref(0)
const users = ref([])
const tickets = ref([])
const options = reactive({ statuses: {}, ticketTypes: {} })
const q = reactive({ current: 1, size: 20, status: '', ticketType: '', keyword: '' })
const emptyForm = () => ({ id: null, title: '', ticketType: '', location: '', workContent: '', departmentName: '', responsibleId: null, timeRange: [], ticketId: null, remark: '' })
const form = reactive(emptyForm())
const rules = {
  title: [{ required: true, message: '请填写作业名称', trigger: 'blur' }],
  ticketType: [{ required: true, message: '请选择作业类型', trigger: 'change' }],
  location: [{ required: true, message: '请填写作业地点', trigger: 'blur' }],
  workContent: [{ required: true, message: '请填写作业内容', trigger: 'blur' }],
  responsibleId: [{ required: true, message: '请选择作业负责人', trigger: 'change' }],
  timeRange: [{ validator: (_, v, cb) => v?.length === 2 ? cb() : cb(new Error('请选择计划开始和结束时间')), trigger: 'change' }]
}
function statusType(status) {
  return ({ PLANNED: 'info', IN_PROGRESS: 'warning', COMPLETED: 'success', CANCELLED: 'danger' })[status] || 'info'
}
async function load() {
  loading.value = true
  try {
    const page = await jobPlanPage(Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null)))
    rows.value = page.records
    total.value = page.total
  } finally { loading.value = false }
}
function search() { q.current = 1; load() }
async function edit(row) {
  Object.assign(form, emptyForm())
  if (row) Object.assign(form, { ...row, timeRange: [row.plannedStartTime, row.plannedEndTime] })
  visible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const body = {
      title: form.title, ticketType: form.ticketType, location: form.location, workContent: form.workContent,
      departmentName: form.departmentName, responsibleId: form.responsibleId,
      plannedStartTime: form.timeRange[0], plannedEndTime: form.timeRange[1],
      ticketId: form.ticketId || null, remark: form.remark
    }
    if (form.id) await updateJobPlan(form.id, body)
    else await saveJobPlan(body)
    ElMessage.success('作业计划已保存')
    visible.value = false
    load()
  } finally { saving.value = false }
}
async function action(row, actionName) {
  const verb = { start: '开始', complete: '完成', cancel: '取消' }[actionName]
  await ElMessageBox.confirm('确认' + verb + '“' + row.title + '”吗？', '作业计划')
  await jobPlanAction(row.id, actionName)
  ElMessage.success('已' + verb)
  load()
}
onMounted(async () => {
  const [opt, userList, ticketPage] = await Promise.all([jobPlanOptions(), userOptions(), pageTickets({ current: 1, size: 100 })])
  Object.assign(options, opt)
  users.value = userList
  tickets.value = ticketPage.records || []
  load()
})
</script>

<style scoped>
.mb { margin-bottom: 12px; }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>