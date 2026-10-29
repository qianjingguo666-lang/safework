<template>
  <el-drawer v-model="visible" size="640px" :title="t ? `${t.ticketNo}　${t.title}` : '作业票详情'">
    <div v-if="t" v-loading="loading">
      <div class="head">
        <el-tag :type="TICKET_STATUS[t.status]?.type" size="large">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
        <span class="type">{{ TICKET_TYPES[t.ticketType]?.label }}</span>
      </div>

      <div v-if="actions.length" class="actions">
        <el-button v-for="a in actions" :key="a" :type="BTN_TYPE[a] || 'default'" :loading="busy === a" @click="run(a)">
          {{ TICKET_ACTIONS[a] }}
        </el-button>
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
import { getTicket, ticketAction } from '@/api'
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

async function open(id) {
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
</style>
