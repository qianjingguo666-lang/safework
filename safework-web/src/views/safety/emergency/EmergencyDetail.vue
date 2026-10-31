<template>
  <el-drawer v-model="visible" size="min(640px, 100vw)" :title="e ? `${e.eventNo}　${e.title}` : '应急事件'">
    <div v-if="e" v-loading="loading">
      <div class="head">
        <el-tag :type="EMERGENCY_STATUS[e.status]?.type" size="large" :effect="e.status === 'RESPONDING' ? 'dark' : 'light'">{{ EMERGENCY_STATUS[e.status]?.label }}</el-tag>
        <el-tag :type="EMERGENCY_LEVELS[e.level]?.type" effect="plain">{{ EMERGENCY_LEVELS[e.level]?.label }}</el-tag>
        <el-tag type="info">{{ ACCIDENT_TYPES[e.accidentType] }}</el-tag>
      </div>

      <div v-if="actions.length" class="actions">
        <el-button v-if="actions.includes('START')" type="danger" icon="AlarmClock" @click="openStart">启动应急响应</el-button>
        <el-button v-if="actions.includes('UPDATE')" type="primary" icon="EditPen" @click="openAction('log')">记录处置情况</el-button>
        <el-button v-if="actions.includes('CLOSE')" icon="CircleCheck" @click="openAction('close')">{{ e.status === 'REPORTED' ? '结束（误报 / 无需响应）' : '结束应急响应' }}</el-button>
      </div>

      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item label="事发时间">{{ e.occurTime }}</el-descriptions-item>
        <el-descriptions-item label="地点">{{ e.location }}</el-descriptions-item>
        <el-descriptions-item label="受限空间" :span="2">{{ e.spaceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="伤亡" :span="2">死亡 {{ e.dead }} 人，重伤 {{ e.seriousInjured }} 人，轻伤 {{ e.minorInjured }} 人</el-descriptions-item>
        <el-descriptions-item label="情况描述" :span="2"><span class="pre">{{ e.description || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="上报人">{{ e.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="上报时间">{{ e.createTime }}</el-descriptions-item>
        <el-descriptions-item label="启动预案">{{ e.planTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="现场指挥">{{ e.commanderName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="响应时间">{{ e.responseTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ e.closeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="e.summary" label="事后总结" :span="2"><span class="pre">{{ e.summary }}</span></el-descriptions-item>
      </el-descriptions>

      <!-- 还没启动响应：AI 帮忙匹配预案 -->
      <div v-if="e.status === 'REPORTED'" class="advice">
        <div class="advice-head">
          <b><el-icon><MagicStick /></el-icon> 预案匹配建议</b>
          <el-button size="small" :loading="advising" @click="advise">{{ advice ? '重新匹配' : '匹配预案' }}</el-button>
        </div>
        <template v-if="advice">
          <div v-if="advice.planId" class="advice-plan">
            建议启动：<b>{{ advice.planCode }} {{ advice.planTitle }}</b>
            <el-tag size="small" :type="advice.aiUsed ? 'success' : 'info'">{{ advice.aiUsed ? 'AI 建议' : '规则匹配' }}</el-tag>
          </div>
          <div v-else class="muted">没有匹配到合适的预案，请人工选择</div>
          <div v-if="advice.reason" class="muted">{{ advice.reason }}</div>
          <template v-if="advice.steps?.length">
            <div class="sub">首要处置步骤</div>
            <ol class="steps"><li v-for="(s, i) in advice.steps" :key="i">{{ s }}</li></ol>
          </template>
          <template v-if="advice.cautions?.length">
            <div class="sub danger">注意事项</div>
            <ul class="cautions"><li v-for="(s, i) in advice.cautions" :key="i">{{ s }}</li></ul>
          </template>
          <div v-if="advice.note" class="muted small">{{ advice.note }}</div>
          <div class="muted small">建议仅供参考，启动哪个预案由安全员决定</div>
        </template>
        <div v-else class="muted small">根据事故类型和描述，从本企业的应急预案里找出最匹配的一份，并摘出首要处置步骤</div>
      </div>

      <template v-if="plan">
        <h4>{{ plan.planCode }} {{ plan.title }}</h4>
        <div v-if="team.length" class="team">应急小组：<el-tag v-for="n in team" :key="n" size="small">{{ n }}</el-tag></div>
        <div class="plan-content pre">{{ plan.content }}</div>
      </template>

      <h4>处置记录</h4>
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createTime" placement="top" :type="LOG_TYPE[l.action]">
          <b>{{ EMERGENCY_LOG_ACTIONS[l.action] || l.action }}</b>　{{ l.operatorName }}
          <div v-if="l.content" class="comment pre">{{ l.content }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-dialog v-model="startVisible" title="启动应急响应" width="min(480px, 94vw)" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="应急预案" required>
          <el-select v-model="act.planId" filterable style="width: 100%" placeholder="选择要启动的预案">
            <el-option v-for="p in plans" :key="p.id" :label="`${p.planCode} ${p.title}`" :value="p.id">
              {{ p.planCode }} {{ p.title }}<span v-if="p.id === advice?.planId" class="suggest">建议</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="act.content" type="textarea" :rows="2" maxlength="2000" placeholder="可选：现场指令，如：全员撤离到东门集合点" /></el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon title="启动后你就是现场指挥，应急小组成员和安全员会立即收到通知" />
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="danger" :loading="busy" @click="submit('start')">确定启动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionVisible" :title="actionKind === 'log' ? '记录处置情况' : '结束'" width="min(520px, 94vw)" append-to-body>
      <el-input v-model="act.content" type="textarea" :rows="5" maxlength="2000" show-word-limit
                :placeholder="actionKind === 'log' ? '如：10:32 救援人员佩戴空气呼吸器进入，被困人员已救出，送医' : '处置经过、原因初步分析、整改措施；误报请说明情况'" />
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="submit(actionKind)">提交</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { emergencyAction, emergencyAdvise, emergencyDetail, emergencyPlans } from '@/api'
import { ACCIDENT_TYPES, EMERGENCY_LEVELS, EMERGENCY_LOG_ACTIONS, EMERGENCY_STATUS } from '@/utils/dict'

const emit = defineEmits(['changed'])
const LOG_TYPE = { REPORT: 'warning', START: 'danger', UPDATE: 'primary', CLOSE: 'success' }
const visible = ref(false)
const loading = ref(false)
const e = ref(null)
const logs = ref([])
const actions = ref([])
const plan = ref(null)
const team = ref([])
const advice = ref(null)
const advising = ref(false)

async function open(id) {
  visible.value = true
  loading.value = true
  if (String(e.value?.id) !== String(id)) advice.value = null
  try {
    const d = await emergencyDetail(id)
    e.value = d.event
    logs.value = d.logs
    actions.value = d.actions || []
    plan.value = d.plan
    team.value = d.team || []
  } finally {
    loading.value = false
  }
}

async function advise() {
  advising.value = true
  try {
    advice.value = await emergencyAdvise(e.value.id)
  } finally {
    advising.value = false
  }
}

const startVisible = ref(false)
const actionVisible = ref(false)
const actionKind = ref('log')
const busy = ref(false)
const plans = ref([])
const act = ref({ planId: null, content: '' })

async function openStart() {
  act.value = { planId: advice.value?.planId || null, content: '' }
  startVisible.value = true
  plans.value = await emergencyPlans({ enabled: true })
}
function openAction(kind) {
  actionKind.value = kind
  act.value = { planId: null, content: '' }
  actionVisible.value = true
}
async function submit(kind) {
  if (kind === 'start' && !act.value.planId) return ElMessage.warning('请选择要启动的应急预案')
  if (kind !== 'start' && !act.value.content.trim()) return ElMessage.warning(kind === 'log' ? '请填写处置情况' : '请填写事后总结')
  busy.value = true
  try {
    await emergencyAction(e.value.id, kind, act.value)
    ElMessage.success({ start: '已启动应急响应，通知已发出', log: '已记录', close: '已结束' }[kind])
    startVisible.value = false
    actionVisible.value = false
    emit('changed')
    await open(e.value.id)
  } finally {
    busy.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; padding: 12px; background: #f6f8fb; border-radius: 6px; }
.actions .el-button { margin-left: 0; }
.block { margin-top: 16px; }
.pre { white-space: pre-wrap; word-break: break-all; }
.advice { margin-top: 16px; padding: 12px 14px; border: 1px solid #d9ecff; background: #f5faff; border-radius: 6px; font-size: 14px; line-height: 1.7; }
.advice-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.advice-head b { display: flex; align-items: center; gap: 4px; color: #337ecc; }
.advice-plan { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.sub { margin-top: 8px; font-weight: 600; }
.sub.danger { color: #c45656; }
.steps, .cautions { margin: 4px 0; padding-left: 22px; }
.cautions { color: #c45656; }
.muted { color: #888; }
.small { font-size: 12px; }
h4 { margin: 20px 0 10px; }
.team { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 8px; font-size: 13px; color: #666; }
.plan-content { max-height: 260px; overflow: auto; padding: 10px 12px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; font-size: 13px; line-height: 1.7; }
.comment { color: #666; margin-top: 4px; font-size: 13px; }
.suggest { margin-left: 8px; color: #67c23a; font-size: 12px; }
</style>
