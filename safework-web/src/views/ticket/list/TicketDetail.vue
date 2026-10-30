<template>
  <el-drawer v-model="visible" size="640px" :title="t ? `${t.ticketNo}　${t.title}` : '作业票详情'">
    <div v-if="t" v-loading="loading">
      <div class="head">
        <el-tag :type="TICKET_STATUS[t.status]?.type" size="large">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
        <span class="type">{{ TICKET_TYPES[t.ticketType]?.label }}</span>
        <div class="spacer" />
        <el-button v-if="t.status !== 'DRAFT'" icon="Printer" size="small" @click="printIt">打印</el-button>
      </div>

      <div v-if="actions.length || canReview" class="actions">
        <el-button v-for="a in actions" :key="a" :type="BTN_TYPE[a] || 'default'" :loading="busy === a" @click="run(a)">
          {{ btnLabel(a) }}
        </el-button>
        <el-button v-if="canReview" icon="MagicStick" :loading="reviewing" class="ai-btn" @click="doReview">AI 预审</el-button>
      </div>

      <div v-if="approval && approval.totalNodes > 1" class="approval">
        <div class="approval-head">
          <b>审批进度</b>
          <span class="muted">共 {{ approval.totalNodes }} 级{{ approval.round > 1 ? `，第 ${approval.round} 次提交` : '' }}</span>
        </div>
        <div v-for="n in approval.nodes" :key="n.index" class="a-node" :class="n.status">
          <div class="dot">{{ NODE_ICON[n.status] || n.index + 1 }}</div>
          <div class="a-body">
            <div class="a-title">
              {{ n.name }}
              <el-tag size="small" effect="plain" :type="n.mode === 'ALL' ? 'warning' : 'info'">{{ n.mode === 'ALL' ? '会签' : '或签' }}</el-tag>
              <span class="a-status">{{ NODE_STATUS[n.status] }}</span>
            </div>
            <div class="muted">{{ n.approverDesc }}</div>
            <div v-for="(k, i) in n.tasks.filter((x) => x.status !== 'CANCELLED' || n.mode === 'ALL')" :key="i" class="a-task">
              <span :class="'t-' + k.status">{{ k.approverName }}　{{ TASK_STATUS[k.status] }}</span>
              <span v-if="k.decideTime" class="muted">{{ k.decideTime }}</span>
              <div v-if="k.comment" class="comment">{{ k.comment }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="review" class="review">
        <div class="review-head">
          <b>AI 预审结果</b>
          <el-tag :type="RISK[review.riskLevel].type" effect="dark">{{ RISK[review.riskLevel].label }}</el-tag>
          <span v-if="review.ai?.conclusion" class="conclusion">{{ review.ai.conclusion }}</span>
          <span class="muted">用时 {{ (review.costMs / 1000).toFixed(1) }} 秒</span>
        </div>
        <p v-if="review.ai?.summary" class="summary">{{ review.ai.summary }}</p>
        <div class="find-title">规则检查（{{ review.ruleFindings.length }}）</div>
        <div v-if="!review.ruleFindings.length" class="muted">未发现问题</div>
        <div v-for="(f, i) in review.ruleFindings" :key="'r' + i" class="finding">
          <el-tag size="small" :type="RISK[f.level]?.type">{{ RISK[f.level]?.short }}</el-tag>
          <span>{{ f.problem }}</span><span class="sugg">→ {{ f.suggestion }}</span>
        </div>
        <template v-if="review.ai?.issues?.length">
          <div class="find-title">大模型分析（{{ review.ai.issues.length }}）</div>
          <div v-for="(f, i) in review.ai.issues" :key="'a' + i" class="finding">
            <el-tag size="small" :type="RISK[f.level]?.type || 'info'">{{ RISK[f.level]?.short || f.level }}</el-tag>
            <span>{{ f.item ? f.item + '：' : '' }}{{ f.problem }}</span><span v-if="f.suggestion" class="sugg">→ {{ f.suggestion }}</span>
          </div>
        </template>
        <el-alert v-if="!review.aiAvailable" type="info" :closable="false" title="未配置大模型，仅展示规则检查结果" class="ai-tip" />
        <el-alert v-else-if="review.aiError" type="warning" :closable="false" :title="`大模型分析失败（规则检查结果仍然有效）：${review.aiError}`" class="ai-tip" />
        <div v-if="review.sources?.length" class="muted refs">参考制度：{{ [...new Set(review.sources.map((s) => '《' + s.documentTitle + '》'))].join('、') }}</div>
        <div class="muted">AI 意见仅供参考，是否批准由审批人决定</div>
      </div>

      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item v-if="t.spaceName" label="受限空间" :span="2">{{ t.spaceName }}</el-descriptions-item>
        <el-descriptions-item label="作业地点" :span="2">{{ t.location }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ t.planStartTime }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ t.planEndTime }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ t.actualStartTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ t.actualEndTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ t.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="监护人">{{ t.guardianName }}</el-descriptions-item>
        <el-descriptions-item label="审批人" :span="2">{{ t.approverName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="作业内容" :span="2">{{ t.workContent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安全措施" :span="2"><span class="pre">{{ t.safetyMeasures || '-' }}</span></el-descriptions-item>
      </el-descriptions>

      <template v-if="TICKET_TYPES[t.ticketType]?.gas">
        <h4>气体检测</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="氧含量">{{ fmt(t.gasOxygen, '%VOL') }}</el-descriptions-item>
          <el-descriptions-item label="可燃气体">{{ fmt(t.gasLel, '%LEL') }}</el-descriptions-item>
          <el-descriptions-item label="硫化氢">{{ fmt(t.gasH2s, 'mg/m³') }}</el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ t.gasCheckTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <AttachmentPanel :key="`a-${t.id}-${t.status}`" biz-type="TICKET" :biz-id="t.id" tag="ATTACH" title="附件"
                       empty-text="申请人、监护人可以上传作业方案、气体检测记录、现场照片（图片、PDF、Word、Excel）" />

      <h4>操作记录</h4>
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createTime" placement="top">
          <b>{{ TICKET_ACTIONS[l.action] }}</b>　{{ l.operatorName }}
          <span v-if="l.toStatus && l.fromStatus !== l.toStatus" class="flow">
            {{ TICKET_STATUS[l.fromStatus]?.label || '' }} → {{ TICKET_STATUS[l.toStatus]?.label }}
          </span>
          <div v-if="l.comment" class="comment">{{ l.comment }}</div>
          <img v-if="l.signImage" :src="l.signImage" class="sign-img" alt="手写签名">
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 开工：监护人现场复测气体 -->
    <el-dialog v-model="startVisible" title="开始作业" width="min(460px, 94vw)" append-to-body destroy-on-close>
      <el-alert v-if="gasRequired" type="warning" :closable="false" show-icon
                title="开工前 30 分钟内必须有合格的气体检测数据，请填写现场复测结果" />
      <el-form label-width="90px" class="start-form">
        <template v-if="gasRequired">
          <el-form-item label="氧含量"><el-input-number v-model="startForm.gasOxygen" :precision="2" :step="0.1" /> %VOL</el-form-item>
          <el-form-item label="可燃气体"><el-input-number v-model="startForm.gasLel" :precision="2" :step="0.1" /> %LEL</el-form-item>
          <el-form-item label="硫化氢"><el-input-number v-model="startForm.gasH2s" :precision="2" :step="0.1" /> mg/m³</el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="startForm.comment" placeholder="如：安全措施已落实" /></el-form-item>
        <el-form-item label="监护人签名" required><SignaturePad ref="startPadRef" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'START'" @click="doStart">确认开工</el-button>
      </template>
    </el-dialog>

    <!-- 提交、审批、驳回、完工、取消：填写意见 + 手写签名 -->
    <el-dialog v-model="actVisible" :title="actTitle" width="min(460px, 94vw)" append-to-body destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="act !== 'SUBMIT'" :label="act === 'REJECT' ? '驳回原因' : '意见'" :required="act === 'REJECT'">
          <el-input v-model="actComment" type="textarea" :rows="2" maxlength="500"
                    :placeholder="act === 'APPROVE' ? '如：安全措施到位，同意作业' : act === 'REJECT' ? '请写明需要修改的地方' : '可选'" />
        </el-form-item>
        <el-form-item v-if="SIGN_ACTIONS.includes(act)" :label="signLabel" required>
          <SignaturePad ref="actPadRef" />
        </el-form-item>
        <div v-if="SIGN_ACTIONS.includes(act)" class="sign-tip">签名会保存在操作记录里，并打印在纸质作业票上</div>
      </el-form>
      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button :type="BTN_TYPE[act] || 'primary'" :loading="busy === act" @click="doAct">{{ actButton }}</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTicket, reviewTicket, ticketAction } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import SignaturePad from '@/components/SignaturePad.vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { TICKET_ACTIONS, TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'

const emit = defineEmits(['changed', 'edit'])
const visible = ref(false)
const loading = ref(false)
const busy = ref('')
const t = ref(null)
const logs = ref([])
const actions = ref([])
const approval = ref(null)
const NODE_STATUS = { WAITING: '未开始', ACTIVE: '审批中', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已终止' }
const NODE_ICON = { APPROVED: '✓', REJECTED: '✕' }
const TASK_STATUS = { PENDING: '待审批', APPROVED: '同意', REJECTED: '驳回', CANCELLED: '未审批' }
// 多级审批：不是最后一个节点时，“同意”是进入下一步
const btnLabel = (a) => {
  const p = approval.value
  if (a === 'APPROVE' && p && p.currentNode != null && p.currentNode < p.totalNodes - 1) return '同意，进入下一步'
  return TICKET_ACTIONS[a]
}
const BTN_TYPE = { SUBMIT: 'primary', APPROVE: 'success', REJECT: 'danger', START: 'warning', COMPLETE: 'success' }
const gasRequired = computed(() => TICKET_TYPES[t.value?.ticketType]?.gas)
const fmt = (v, unit) => (v == null ? '-' : `${v} ${unit}`)

const store = useUserStore()
const canReview = computed(() => store.hasPerm('ai:ticket:review') && ['DRAFT', 'PENDING', 'REJECTED', 'APPROVED'].includes(t.value?.status))
const RISK = {
  HIGH: { label: '高风险', short: '必须整改', type: 'danger' },
  MEDIUM: { label: '中风险', short: '建议补充', type: 'warning' },
  LOW: { label: '低风险', short: '提示', type: 'success' }
}
const review = ref(null)
const reviewing = ref(false)
async function doReview() {
  reviewing.value = true
  try {
    review.value = await reviewTicket(t.value.id)
  } finally {
    reviewing.value = false
  }
}

async function open(id) {
  if (t.value?.id !== id) review.value = null
  visible.value = true
  loading.value = true
  try {
    const d = await getTicket(id)
    t.value = d.ticket
    logs.value = d.logs
    actions.value = d.actions
    approval.value = d.approval
  } finally {
    loading.value = false
  }
}

async function call(action, data) {
  busy.value = action
  const toNext = btnLabel(action) !== TICKET_ACTIONS[action]
  try {
    await ticketAction(t.value.id, action.toLowerCase(), data)
    ElMessage.success(toNext ? '已同意，进入下一个审批节点' : `${TICKET_ACTIONS[action]}成功`)
    emit('changed')
    await open(t.value.id)
  } finally {
    busy.value = ''
  }
}

// 按钮由后端返回的 actions 决定，这里只负责收集参数
async function run(action) {
  try {
    await runInner(action)
  } catch (e) {
    // 用户点了弹窗的“取消”，不算错误
    if (e !== 'cancel' && e !== 'close') throw e
  }
}

async function runInner(action) {
  if (action === 'EDIT') {
    visible.value = false
    emit('edit', t.value)
    return
  }
  if (action === 'START') {
    Object.assign(startForm, { gasOxygen: null, gasLel: null, gasH2s: null, comment: '' })
    startVisible.value = true
    return
  }
  openAct(action)
}

// ---------------- 意见 + 手写签名 ----------------
// 这些操作要求手写签名：申请人提交、审批人同意、监护人开工和完工
const SIGN_ACTIONS = ['SUBMIT', 'APPROVE', 'START', 'COMPLETE']
const SIGN_ROLE = { SUBMIT: '申请人签名', APPROVE: '审批人签名', COMPLETE: '监护人签名' }
const actVisible = ref(false)
const act = ref('')
const actComment = ref('')
const actPadRef = ref()
const actTitle = computed(() => (act.value === 'APPROVE' ? btnLabel('APPROVE') : TICKET_ACTIONS[act.value]))
const actButton = computed(() => (act.value === 'CANCEL' ? '确认取消作业票' : '确认' + (act.value === 'APPROVE' ? '同意' : TICKET_ACTIONS[act.value])))
const signLabel = computed(() => SIGN_ROLE[act.value] || '签名')
function openAct(action) {
  act.value = action
  actComment.value = ''
  actVisible.value = true
}
async function doAct() {
  const action = act.value
  if (action === 'REJECT' && !actComment.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  const data = { comment: actComment.value.trim() || undefined }
  if (SIGN_ACTIONS.includes(action)) {
    data.signature = actPadRef.value?.toDataURL()
    if (!data.signature) {
      ElMessage.warning('请先手写签名')
      return
    }
  }
  await call(action, data)
  actVisible.value = false
}

// 打印：新窗口打开 A4 票面，浏览器打印或另存为 PDF
const router = useRouter()
function printIt() {
  window.open(router.resolve(`/ticket/print/${t.value.id}`).href, '_blank')
}

const startVisible = ref(false)
const startForm = reactive({ gasOxygen: null, gasLel: null, gasH2s: null, comment: '' })
const startPadRef = ref()
async function doStart() {
  const signature = startPadRef.value?.toDataURL()
  if (!signature) {
    ElMessage.warning('请监护人手写签名，确认现场安全措施已落实')
    return
  }
  await call('START', { ...startForm, signature })
  startVisible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; }
.spacer { flex: 1; }
.sign-img { display: block; max-width: 180px; max-height: 60px; margin-top: 6px; padding: 2px 6px; border: 1px solid #ebeef5; border-radius: 4px; background: #fff; }
.sign-tip { color: #999; font-size: 12px; margin-top: -8px; }
.type { color: #666; }
.approval { margin: 16px 0; padding: 12px 14px; border: 1px solid #e4e8f0; border-radius: 6px; }
.approval-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; }
.muted { color: #999; font-size: 12px; }
.a-node { display: flex; gap: 10px; position: relative; padding-bottom: 12px; }
.a-node:not(:last-child)::before { content: ''; position: absolute; left: 11px; top: 26px; bottom: 2px; border-left: 2px solid #e4e8f0; }
.a-node:last-child { padding-bottom: 0; }
.dot { flex: none; width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; background: #eef1f6; color: #909399; }
.ACTIVE .dot { background: #e6a23c; color: #fff; }
.APPROVED .dot { background: #67c23a; color: #fff; }
.REJECTED .dot { background: #f56c6c; color: #fff; }
.a-body { flex: 1; min-width: 0; }
.a-title { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.a-status { font-weight: normal; font-size: 12px; color: #909399; }
.ACTIVE .a-status { color: #e6a23c; }
.APPROVED .a-status { color: #67c23a; }
.REJECTED .a-status { color: #f56c6c; }
.a-task { font-size: 13px; margin-top: 4px; display: flex; flex-wrap: wrap; gap: 0 8px; align-items: baseline; }
.a-task .comment { flex-basis: 100%; }
.t-APPROVED { color: #67c23a; }
.t-REJECTED { color: #f56c6c; }
.t-PENDING { color: #e6a23c; }
.t-CANCELLED { color: #c0c4cc; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; padding: 12px; background: #f6f8fb; border-radius: 6px; }
.block { margin-top: 16px; }
h4 { margin: 20px 0 10px; }
.pre { white-space: pre-wrap; }
.flow { color: #888; margin-left: 8px; font-size: 12px; }
.comment { margin-top: 4px; padding: 6px 10px; background: #f6f8fb; border-radius: 4px; color: #555; }
.start-form { margin-top: 16px; }
.ai-btn { margin-left: auto; color: #6b46c1; border-color: #d6c8f5; }
.review { border: 1px solid #e3d9fb; background: #faf8ff; border-radius: 8px; padding: 12px 14px; margin-bottom: 8px; font-size: 13px; }
.review-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.conclusion { font-weight: 600; color: #6b46c1; }
.summary { margin: 8px 0 0; color: #444; }
.find-title { margin: 10px 0 4px; color: #888; font-size: 12px; }
.finding { display: flex; gap: 8px; align-items: baseline; line-height: 1.9; }
.sugg { color: #888; }
.ai-tip { margin-top: 8px; }
.refs { margin-top: 8px; }
.muted { color: #999; font-size: 12px; }
</style>
