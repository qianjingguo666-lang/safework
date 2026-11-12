<template>
  <div class="page-card">
    <el-alert type="info" :closable="false" show-icon class="mb"
      title="从已批准的作业票生成交底快照，作业负责人、监护人和所选人员逐人手写签字。启用交底后，所有人签字完成才能开工。" />
    <div class="toolbar">
      <el-select v-model="q.status" clearable placeholder="交底状态" style="width: 130px" @change="search">
        <el-option label="待签字" value="PENDING" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-input v-model="q.keyword" clearable placeholder="作业票编号 / 作业名称" style="width: 240px" @keyup.enter="search" @clear="search" />
      <el-button icon="Search" @click="search">查询</el-button>
      <div class="spacer" />
      <el-button v-if="canManage" type="primary" icon="Plus" @click="openCreate">创建安全交底</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="briefing.ticketNo" label="作业票编号" width="170" />
      <el-table-column prop="briefing.title" label="作业名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="签字进度" width="130"><template #default="{ row }">{{ row.signedCount }} / {{ row.attendeeCount }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }">
        <el-tag :type="row.briefing.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.briefing.status === 'COMPLETED' ? '已完成' : '待签字' }}</el-tag>
      </template></el-table-column>
      <el-table-column prop="briefing.creatorName" label="创建人" width="110" />
      <el-table-column prop="briefing.createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" width="100"><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row.briefing.id)">查看 / 签字</el-button>
      </template></el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="q.current" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="createVisible" title="创建作业安全交底" width="620px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="rules" label-width="120px">
        <el-form-item label="已批准作业票" prop="ticketId">
          <el-select v-model="createForm.ticketId" filterable placeholder="选择尚未创建交底的作业票" style="width: 100%">
            <el-option v-for="t in approvedTickets" :key="t.id" :label="t.ticketNo + ' · ' + t.title" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="其他参与人">
          <el-select v-model="createForm.attendeeIds" multiple filterable placeholder="可添加其他作业人员" style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname + '（' + u.username + '）'" :value="u.id" />
          </el-select>
          <div class="hint">作业负责人和监护人会自动加入交底人员。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建并通知参与人</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="作业安全交底" width="760px" destroy-on-close>
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="作业票">{{ detail.briefing.ticketNo }}</el-descriptions-item>
          <el-descriptions-item label="作业名称">{{ detail.briefing.title }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detail.briefing.status === 'COMPLETED' ? 'success' : 'warning'">{{ detail.briefing.status === 'COMPLETED' ? '已完成' : '待签字' }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.briefing.creatorName }}</el-descriptions-item>
        </el-descriptions>
        <h4>作业内容与安全措施（创建时快照）</h4>
        <pre class="snapshot">{{ detail.briefing.contentSnapshot }}</pre>
        <h4>参与人签字</h4>
        <el-table :data="detail.attendees" size="small" border>
          <el-table-column prop="participantName" label="姓名" width="140" />
          <el-table-column label="角色" width="120"><template #default="{ row }">{{ roleLabel(row.participantRole) }}</template></el-table-column>
          <el-table-column label="签字状态" width="100"><template #default="{ row }"><el-tag :type="row.signedTime ? 'success' : 'warning'">{{ row.signedTime ? '已签字' : '待签字' }}</el-tag></template></el-table-column>
          <el-table-column prop="signedTime" label="签字时间" min-width="160" />
          <el-table-column label="签名" min-width="140"><template #default="{ row }"><img v-if="row.signImage" :src="row.signImage" class="signature" alt="手写签名" /></template></el-table-column>
        </el-table>
        <div v-if="mine && !mine.signedTime && detail.briefing.status === 'PENDING'" class="sign-area">
          <h4>请确认已理解作业风险和安全措施，并手写签名</h4>
          <SignaturePad ref="padRef" placeholder="请在此手写签名" />
          <el-button type="primary" :loading="signing" class="sign-button" @click="sign">签字确认</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { pageTickets, userOptions, workBriefingPage, workBriefingDetail, createWorkBriefing, confirmWorkBriefing } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'

const store = useUserStore()
const canManage = computed(() => store.hasPerm('briefing:manage'))
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const signing = ref(false)
const rows = ref([])
const total = ref(0)
const approvedTickets = ref([])
const users = ref([])
const detail = ref(null)
const detailVisible = ref(false)
const createVisible = ref(false)
const createRef = ref()
const padRef = ref()
const q = reactive({ current: 1, size: 20, status: '', keyword: '' })
const createForm = reactive({ ticketId: null, attendeeIds: [] })
const rules = { ticketId: [{ required: true, message: '请选择作业票', trigger: 'change' }] }
const mine = computed(() => detail.value?.attendees?.find((a) => a.mine))
function roleLabel(role) { return ({ APPLICANT: '作业负责人', GUARDIAN: '监护人', MEMBER: '作业人员' })[role] || role }

async function load() {
  loading.value = true
  try {
    const p = await workBriefingPage(Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null)))
    rows.value = p.records
    total.value = p.total
  } finally { loading.value = false }
}
function search() { q.current = 1; load() }
async function openCreate() {
  Object.assign(createForm, { ticketId: null, attendeeIds: [] })
  createVisible.value = true
}
async function create() {
  await createRef.value.validate()
  saving.value = true
  try {
    await createWorkBriefing({ ticketId: createForm.ticketId, attendeeIds: createForm.attendeeIds })
    ElMessage.success('交底已创建，已通知参与人签字')
    createVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  try { detail.value = await workBriefingDetail(id) } finally { detailLoading.value = false }
}
async function sign() {
  const signature = padRef.value?.toDataURL()
  if (!signature) return ElMessage.warning('请先手写签名')
  await ElMessageBox.confirm('确认已理解作业风险及控制措施，并提交本人签名吗？', '安全交底确认')
  signing.value = true
  try {
    await confirmWorkBriefing(detail.value.briefing.id, signature)
    ElMessage.success('签字已提交')
    await openDetail(detail.value.briefing.id)
    await load()
  } finally { signing.value = false }
}
onMounted(async () => {
  const [ticketPage, userList] = await Promise.all([
    pageTickets({ current: 1, size: 100, status: 'APPROVED' }),
    userOptions()
  ])
  approvedTickets.value = ticketPage.records || []
  users.value = userList
  load()
})
</script>

<style scoped>
.mb { margin-bottom: 12px; }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.hint { color: #909399; font-size: 12px; line-height: 1.6; }
.snapshot { white-space: pre-wrap; word-break: break-word; padding: 12px; background: #f6f8fa; border-radius: 6px; font: inherit; line-height: 1.7; }
.sign-area { margin-top: 16px; }
.sign-button { margin-top: 10px; }
.signature { max-width: 120px; max-height: 46px; object-fit: contain; }
</style>