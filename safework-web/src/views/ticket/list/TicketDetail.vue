<template>
  <el-drawer v-model="visible" size="640px" :title="t ? `${t.ticketNo}　${t.title}` : '作业票详情'">
    <div v-if="t" v-loading="loading">
      <div class="head">
        <el-tag :type="TICKET_STATUS[t.status]?.type" size="large">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
        <span class="type">{{ TICKET_TYPES[t.ticketType]?.label }}</span>
      </div>

      <div v-if="actions.length || canReview" class="actions">
        <el-button v-for="a in actions" :key="a" :type="BTN_TYPE[a] || 'default'" :loading="busy === a" @click="run(a)">
          {{ TICKET_ACTIONS[a] }}
        </el-button>
        <el-button v-if="canReview" icon="MagicStick" :loading="reviewing" class="ai-btn" @click="doReview">AI 预审</el-button>
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
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 开工：监护人现场复测气体 -->
    <el-dialog v-model="startVisible" title="开始作业" width="420px" append-to-body>
      <el-alert v-if="gasRequired" type="warning" :closable="false" show-icon
                title="开工前 30 分钟内必须有合格的气体检测数据，请填写现场复测结果" />
      <el-form label-width="90px" class="start-form">
        <template v-if="gasRequired">
          <el-form-item label="氧含量"><el-input-number v-model="startForm.gasOxygen" :precision="2" :step="0.1" /> %VOL</el-form-item>
          <el-form-item label="可燃气体"><el-input-number v-model="startForm.gasLel" :precision="2" :step="0.1" /> %LEL</el-form-item>
          <el-form-item label="硫化氢"><el-input-number v-model="startForm.gasH2s" :precision="2" :step="0.1" /> mg/m³</el-form-item>
        </template>
        <el-form-item label="备注"><el-input v-model="startForm.comment" placeholder="如：安全措施已落实" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'START'" @click="doStart">确认开工</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTicket, reviewTicket, ticketAction } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { useUserStore } from '@/stores/user'
import { TICKET_ACTIONS, TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'

const emit = defineEmits(['changed', 'edit'])
const visible = ref(false)
const loading = ref(false)
const busy = ref('')
const t = ref(null)
const logs = ref([])
const actions = ref([])
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
  } finally {
    loading.value = false
  }
}

async function call(action, data) {
  busy.value = action
  try {
    await ticketAction(t.value.id, action.toLowerCase(), data)
    ElMessage.success(`${TICKET_ACTIONS[action]}成功`)
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
  if (action === 'SUBMIT' || action === 'COMPLETE') {
    await ElMessageBox.confirm(`确认${TICKET_ACTIONS[action]}？`, '提示', { type: 'warning' })
    return call(action, {})
  }
  // APPROVE / REJECT / CANCEL 需要填写意见，驳回必填
  const required = action === 'REJECT'
  const { value } = await ElMessageBox.prompt(required ? '请填写驳回原因' : '意见（可选）', TICKET_ACTIONS[action], {
    inputValidator: (v) => !required || (v && v.trim().length > 0) || '驳回原因不能为空',
    inputPlaceholder: action === 'APPROVE' ? '如：安全措施到位，同意作业' : ''
  })
  return call(action, { comment: value })
}

const startVisible = ref(false)
const startForm = reactive({ gasOxygen: null, gasLel: null, gasH2s: null, comment: '' })
async function doStart() {
  await call('START', { ...startForm })
  startVisible.value = false
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; }
.type { color: #666; }
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
