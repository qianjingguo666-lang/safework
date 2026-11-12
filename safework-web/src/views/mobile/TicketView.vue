<template>
  <div v-loading="loading">
    <template v-if="t">
      <div class="m-card">
        <div class="m-item-head">
          <span class="m-item-title big">{{ t.title }}</span>
          <el-tag :type="TICKET_STATUS[t.status]?.type" effect="dark">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
        </div>
        <div class="m-muted">{{ t.ticketNo }} · {{ TICKET_TYPES[t.ticketType]?.label }}</div>
        <div v-if="t.status === 'PENDING' && t.currentStep" class="step">当前：{{ t.currentStep }}</div>
      </div>

      <div class="m-card">
        <div v-if="t.spaceName" class="m-row"><span>受限空间</span><span>{{ t.spaceName }}</span></div>
        <div class="m-row"><span>地点</span><span>{{ t.location }}</span></div>
        <div class="m-row"><span>计划时间</span><span>{{ shortTime(t.planStartTime) }} ~ {{ shortTime(t.planEndTime) }}</span></div>
        <div v-if="t.actualStartTime" class="m-row"><span>实际开工</span><span>{{ shortTime(t.actualStartTime) }}</span></div>
        <div class="m-row"><span>申请人</span><span>{{ t.applicantName }}</span></div>
        <div class="m-row"><span>监护人</span><span>{{ t.guardianName }}</span></div>
        <div v-if="t.contractorName" class="m-row"><span>承包商</span><span>{{ t.contractorName }}</span></div>
        <div v-if="t.contractorName" class="m-row"><span>作业人员</span><span>{{ t.contractorWorkerNames }}</span></div>
        <div class="m-row"><span>作业内容</span><span>{{ t.workContent || '-' }}</span></div>
      </div>

      <div class="m-card">
        <h3>安全措施</h3>
        <div class="pre">{{ t.safetyMeasures || '-' }}</div>
      </div>

      <div v-if="briefing" class="m-card">
        <div class="m-item-head">
          <h3>作业安全交底</h3>
          <el-tag :type="briefing.briefing.status === 'COMPLETED' ? 'success' : 'warning'">
            {{ briefing.briefing.status === 'COMPLETED' ? '全员已签' : '待签字' }}
          </el-tag>
        </div>
        <pre class="briefing-snapshot">{{ briefing.briefing.contentSnapshot }}</pre>
        <div v-for="a in briefing.attendees" :key="a.id" class="briefing-person">
          <span>{{ a.participantName }} · {{ ({ APPLICANT: '作业负责人', GUARDIAN: '监护人', MEMBER: '作业人员' })[a.participantRole] }}</span>
          <el-tag size="small" :type="a.signedTime ? 'success' : 'warning'">{{ a.signedTime ? '已签字' : '待签字' }}</el-tag>
        </div>
        <div v-if="mineBriefing && !mineBriefing.signedTime && briefing.briefing.status === 'PENDING'" class="briefing-sign">
          <div class="m-muted">请确认理解作业风险和安全措施，并手写签字</div>
          <SignaturePad ref="briefingPad" placeholder="用手指在这里签名" />
          <el-button type="primary" class="briefing-button" :loading="briefingSigning" @click="signBriefing">签字确认</el-button>
        </div>
      </div>

      <div v-if="TICKET_TYPES[t.ticketType]?.gas" class="m-card">
        <h3>气体检测 <span v-if="t.gasCheckTime" class="m-muted">{{ shortTime(t.gasCheckTime) }}</span></h3>
        <div class="gas">
          <div><b>{{ t.gasOxygen ?? '-' }}</b><span>氧含量 %VOL</span></div>
          <div><b>{{ t.gasLel ?? '-' }}</b><span>可燃气体 %LEL</span></div>
          <div><b>{{ t.gasH2s ?? '-' }}</b><span>硫化氢 mg/m³</span></div>
        </div>
      </div>

      <div v-if="approval && approval.totalNodes > 1" class="m-card">
        <h3>审批进度</h3>
        <div v-for="n in approval.nodes" :key="n.index" class="node" :class="n.status">
          <i>{{ n.status === 'APPROVED' ? '✓' : n.status === 'REJECTED' ? '✕' : n.index + 1 }}</i>
          <div>
            <b>{{ n.name }}</b>
            <span class="m-muted">　{{ n.tasks.map((k) => k.approverName).join('、') || n.approverDesc }}</span>
          </div>
        </div>
      </div>

      <div class="m-card">
        <AttachmentPanel :key="`a-${t.id}-${t.status}`" biz-type="TICKET" :biz-id="t.id" tag="ATTACH" title="附件 / 现场照片"
                         empty-text="暂无附件" />
      </div>

      <div class="m-card">
        <h3>操作记录</h3>
        <div class="m-timeline">
          <div v-for="l in logs" :key="l.id" class="m-log">
            <b>{{ TICKET_ACTIONS[l.action] }}</b>　{{ l.operatorName }}
            <div class="m-muted">{{ shortTime(l.createTime) }}</div>
            <div v-if="l.comment" class="comment">{{ l.comment }}</div>
            <img v-if="l.signImage" :src="l.signImage" alt="签名">
          </div>
        </div>
      </div>

      <div v-if="mobileActions.length" class="m-actions">
        <el-button v-for="a in mobileActions" :key="a" :type="BTN[a] || 'default'" :plain="a === 'REJECT' || a === 'CANCEL'" @click="openAct(a)">
          {{ label(a) }}
        </el-button>
      </div>
      <div v-else-if="actions.includes('EDIT')" class="m-muted center">修改作业票请到电脑端操作</div>
    </template>

    <!-- 意见 + 签名（开工时还要填现场复测的气体数据） -->
    <el-drawer v-model="sheet" direction="btt" size="auto" :title="label(act)" class="m-sheet" append-to-body destroy-on-close>
      <el-form label-position="top">
        <template v-if="act === 'START' && gasRequired">
          <el-alert type="warning" :closable="false" title="开工前 30 分钟内必须有合格的气体检测数据" style="margin-bottom: 12px" />
          <div class="gas-input">
            <el-form-item label="氧含量 %VOL"><el-input v-model="form.gasOxygen" type="number" inputmode="decimal" placeholder="19.5~23.5" /></el-form-item>
            <el-form-item label="可燃 %LEL"><el-input v-model="form.gasLel" type="number" inputmode="decimal" /></el-form-item>
            <el-form-item label="硫化氢 mg/m³"><el-input v-model="form.gasH2s" type="number" inputmode="decimal" /></el-form-item>
          </div>
        </template>
        <el-form-item :label="act === 'REJECT' ? '驳回原因' : '意见（可选）'" :required="act === 'REJECT'">
          <el-input v-model="form.comment" type="textarea" :rows="2" maxlength="500"
                    :placeholder="act === 'APPROVE' ? '如：安全措施到位，同意作业' : act === 'REJECT' ? '请写明需要修改的地方' : ''" />
        </el-form-item>
        <el-form-item v-if="SIGN_ACTIONS.includes(act)" :label="SIGN_LABEL[act]" required>
          <SignaturePad ref="padRef" placeholder="用手指在这里签名" />
        </el-form-item>
        <el-button :type="BTN[act] || 'primary'" class="m-sheet-btn" :loading="busy" @click="submit">确认{{ label(act) }}</el-button>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTicket, ticketAction, workBriefingByTicket, confirmWorkBriefing } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import SignaturePad from '@/components/SignaturePad.vue'
import { TICKET_ACTIONS, TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'
import { shortTime } from '@/utils/mobile'

const emit = defineEmits(['unread'])
const route = useRoute()
const loading = ref(false)
const t = ref(null)
const logs = ref([])
const actions = ref([])
const approval = ref(null)
const briefing = ref(null)
const briefingPad = ref()
const briefingSigning = ref(false)
const mineBriefing = computed(() => briefing.value?.attendees?.find((a) => a.mine))
// 按钮由后端返回的 actions 决定；修改作业票表单太复杂，留在电脑端
const ORDER = ['APPROVE', 'START', 'COMPLETE', 'SUBMIT', 'REJECT', 'CANCEL']
const mobileActions = computed(() => ORDER.filter((a) => actions.value.includes(a)))
const BTN = { SUBMIT: 'primary', APPROVE: 'success', REJECT: 'danger', START: 'warning', COMPLETE: 'success', CANCEL: 'info' }
const SIGN_ACTIONS = ['SUBMIT', 'APPROVE', 'START', 'COMPLETE']
const SIGN_LABEL = { SUBMIT: '申请人签名', APPROVE: '审批人签名', START: '监护人签名', COMPLETE: '监护人签名' }
const gasRequired = computed(() => TICKET_TYPES[t.value?.ticketType]?.gas)
const label = (a) => {
  const p = approval.value
  if (a === 'APPROVE' && p && p.currentNode != null && p.currentNode < p.totalNodes - 1) return '同意，进入下一步'
  return TICKET_ACTIONS[a] || ''
}

async function signBriefing() {
  const signature = briefingPad.value?.toDataURL()
  if (!signature) return ElMessage.warning('请先手写签名')
  await ElMessageBox.confirm('确认已理解作业风险及控制措施，并提交本人签名吗？', '安全交底确认')
  briefingSigning.value = true
  try {
    await confirmWorkBriefing(briefing.value.briefing.id, signature)
    ElMessage.success('签字已提交')
    briefing.value = await workBriefingByTicket(route.params.id)
  } finally {
    briefingSigning.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const d = await getTicket(route.params.id)
    t.value = d.ticket
    logs.value = d.logs
    actions.value = d.actions
    approval.value = d.approval
    briefing.value = await workBriefingByTicket(route.params.id)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const sheet = ref(false)
const act = ref('')
const busy = ref(false)
const padRef = ref()
const form = reactive({})
function openAct(a) {
  act.value = a
  Object.assign(form, { comment: '', gasOxygen: '', gasLel: '', gasH2s: '' })
  sheet.value = true
}
async function submit() {
  const a = act.value
  if (a === 'REJECT' && !form.comment.trim()) return ElMessage.warning('请填写驳回原因')
  const data = { comment: form.comment.trim() || undefined }
  if (a === 'START' && gasRequired.value) {
    if (form.gasOxygen === '' || form.gasLel === '') return ElMessage.warning('请填写氧含量和可燃气体检测值')
    Object.assign(data, { gasOxygen: Number(form.gasOxygen), gasLel: Number(form.gasLel), gasH2s: form.gasH2s === '' ? null : Number(form.gasH2s) })
  }
  if (SIGN_ACTIONS.includes(a)) {
    data.signature = padRef.value?.toDataURL()
    if (!data.signature) return ElMessage.warning('请先手写签名')
  }
  busy.value = true
  try {
    await ticketAction(t.value.id, a.toLowerCase(), data)
    ElMessage.success(`${label(a)}成功`)
    sheet.value = false
    emit('unread')
    await load()
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.big { font-size: 17px; white-space: normal; }
.step { margin-top: 6px; font-size: 13px; color: #e6a23c; }
.pre { white-space: pre-wrap; line-height: 1.7; }
.gas { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; text-align: center; }
.gas div { background: #f6f8fb; border-radius: 8px; padding: 10px 2px; }
.gas b { display: block; font-size: 20px; color: #1f6feb; }
.gas span { font-size: 11px; color: #8a8f99; }
.gas-input { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.node { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.node i { flex: none; width: 22px; height: 22px; border-radius: 50%; background: #eef1f6; color: #909399; font-style: normal; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.node.ACTIVE i { background: #e6a23c; color: #fff; }
.node.APPROVED i { background: #67c23a; color: #fff; }
.node.REJECTED i { background: #f56c6c; color: #fff; }
.center { text-align: center; padding: 8px 0 20px; }
 .briefing-snapshot { white-space: pre-wrap; word-break: break-word; font: inherit; line-height: 1.6; background: #f6f8fa; padding: 10px; border-radius: 6px; }
.briefing-person { display: flex; justify-content: space-between; align-items: center; padding: 7px 0; border-bottom: 1px solid #f0f0f0; }
.briefing-sign { margin-top: 12px; }
.briefing-button { width: 100%; margin-top: 10px; }
</style>
