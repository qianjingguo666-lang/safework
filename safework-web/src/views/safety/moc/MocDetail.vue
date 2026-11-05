<template>
  <el-drawer v-model="visible" size="920px" destroy-on-close :title="c ? `${c.changeNo}　${c.title}` : '变更'">
    <div v-loading="loading" class="wrap">
      <template v-if="d">
        <el-steps v-if="c.status !== 'CANCELLED'" :active="stepIndex" finish-status="success" align-center class="steps">
          <el-step v-for="s in MOC_STEPS" :key="s" :title="MOC_STATUS[s].label" />
        </el-steps>
        <el-alert v-else title="这项变更已取消" type="info" :closable="false" show-icon class="steps" />

        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="类型">{{ MOC_TYPES[c.changeType] }}</el-descriptions-item>
          <el-descriptions-item label="期限">
            <template v-if="c.temporary">
              临时，{{ c.restoreDate }} 恢复
              <el-tag v-if="c.restored" size="small" type="success">已恢复</el-tag>
              <el-tag v-else-if="c.status === 'CLOSED' && c.restoreDate < todayStr" size="small" type="danger" effect="dark">超期未恢复</el-tag>
            </template>
            <template v-else>永久</template>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ c.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="地点 / 装置" :span="3">{{ c.location }}</el-descriptions-item>
          <el-descriptions-item label="变更原因" :span="3">{{ c.reason }}</el-descriptions-item>
          <el-descriptions-item label="变更内容" :span="3"><div class="pre">{{ c.content }}</div></el-descriptions-item>
          <el-descriptions-item v-if="c.restoreNote" label="恢复情况" :span="3">{{ c.restoreNote }}（{{ c.restoreTime?.slice(0, 16) }}）</el-descriptions-item>
        </el-descriptions>

        <!-- 风险评估 -->
        <section>
          <h4>风险评估（LEC）</h4>
          <template v-if="can('ASSESS')">
            <div class="lec">
              <el-select v-model="a.lecL" placeholder="L 可能性"><el-option v-for="o in opts?.lec.l" :key="o.value" :label="`L ${o.value} ${o.label}`" :value="o.value" /></el-select>
              <el-select v-model="a.lecE" placeholder="E 暴露频率"><el-option v-for="o in opts?.lec.e" :key="o.value" :label="`E ${o.value} ${o.label}`" :value="o.value" /></el-select>
              <el-select v-model="a.lecC" placeholder="C 后果"><el-option v-for="o in opts?.lec.c" :key="o.value" :label="`C ${o.value} ${o.label}`" :value="o.value" /></el-select>
              <span v-if="calc" class="d">D = <b>{{ calc.d }}</b>
                <span class="grade" :style="{ background: RISK_GRADES[calc.level].color, color: RISK_GRADES[calc.level].text }">{{ RISK_GRADES[calc.level].label }}</span>
                需要 {{ ['MAJOR', 'HIGH'].includes(calc.level) ? 2 : 1 }} 人审批
              </span>
            </div>
            <el-input v-model="a.riskAnalysis" type="textarea" :rows="2" maxlength="1000" placeholder="风险分析：变更可能带来哪些新的危险（工艺、设备、人员、作业）" class="mt" />
            <el-input v-model="a.measures" type="textarea" :rows="2" maxlength="1000" placeholder="控制措施：实施和投用时要落实的措施" class="mt" />
            <div class="mt">
              <el-button type="primary" :loading="saving" @click="assess">完成评估，提交审批</el-button>
              <el-button @click="act('return', '退回原因', true)">退回申请人</el-button>
            </div>
          </template>
          <template v-else-if="c.riskLevel">
            <div class="risk">
              D = {{ c.lecL }} × {{ c.lecE }} × {{ c.lecC }} = <b>{{ c.lecD }}</b>
              <span class="grade" :style="{ background: RISK_GRADES[c.riskLevel].color, color: RISK_GRADES[c.riskLevel].text }">{{ RISK_GRADES[c.riskLevel].label }}</span>
              <span class="muted">{{ c.assessorName }}<template v-if="c.assessTime"> · {{ c.assessTime.slice(0, 16) }}</template> · 需要 {{ c.approvalsNeeded }} 人审批</span>
            </div>
            <div class="kv"><span>风险分析</span><p>{{ c.riskAnalysis }}</p></div>
            <div class="kv"><span>控制措施</span><p>{{ c.measures }}</p></div>
          </template>
          <div v-else class="muted">还没有评估</div>
        </section>

        <!-- 审批 -->
        <section v-if="c.status === 'APPROVING' || approvals.length">
          <h4>审批 <span v-if="c.status === 'APPROVING'" class="muted">（本轮 {{ d.approved }}/{{ c.approvalsNeeded }} 人已批）</span></h4>
          <div v-for="l in approvals" :key="l.id" class="appr" :class="l.action === 'REJECT' ? 'bad' : 'good'">
            <b>{{ l.operatorName }}</b> {{ l.content }} <span class="muted">{{ l.createTime?.slice(0, 16) }}</span>
          </div>
          <div v-if="can('APPROVE')" class="mt">
            <el-button type="success" @click="act('approve', '审批意见（可不填）', false)">审批通过</el-button>
            <el-button type="danger" plain @click="act('reject', '驳回原因', true)">驳回</el-button>
          </div>
        </section>

        <!-- 实施 -->
        <section v-if="c.implementNote || can('IMPLEMENTED')">
          <h4>实施</h4>
          <div v-if="c.implementNote" class="kv"><span>实施情况</span><p>{{ c.implementNote }}<template v-if="c.implementTime">（{{ c.implementTime.slice(0, 16) }}）</template></p></div>
          <el-button v-if="can('IMPLEMENTED')" type="primary" @click="act('implemented', '实施情况（做了什么、控制措施怎么落实的）', true)">实施完成，申请投用前检查</el-button>
        </section>

        <!-- PSSR -->
        <section v-if="['PSSR', 'CLOSED', 'IMPLEMENTING'].includes(c.status) && (can('PSSR') || c.pssrBy)">
          <h4>投用前安全检查（PSSR）<span v-if="c.pssrBy" class="muted">{{ c.pssrBy }} · {{ c.pssrTime?.slice(0, 16) }}</span></h4>
          <el-table :data="pssrRows" size="small" border>
            <el-table-column type="index" width="44" />
            <el-table-column prop="item" label="检查项" min-width="280" />
            <el-table-column label="结果" width="170">
              <template #default="{ row, $index }">
                <el-radio-group v-if="can('PSSR')" v-model="p.results[$index]" size="small">
                  <el-radio-button :value="true">合格</el-radio-button>
                  <el-radio-button :value="false">不合格</el-radio-button>
                </el-radio-group>
                <el-tag v-else-if="row.ok != null" size="small" :type="row.ok ? 'success' : 'danger'">{{ row.ok ? '合格' : '不合格' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="180">
              <template #default="{ row, $index }">
                <el-input v-if="can('PSSR')" v-model="p.notes[$index]" size="small" :placeholder="p.results[$index] === false ? '不合格原因（必填）' : '可不填'" />
                <span v-else>{{ row.note || '' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="can('PSSR')" class="mt">
            <el-button type="primary" :loading="saving" @click="pssr">提交检查结果</el-button>
            <span class="muted">全部合格才能投用；有不合格退回实施</span>
          </div>
        </section>

        <!-- 时间线 -->
        <section>
          <h4>操作记录</h4>
          <el-timeline>
            <el-timeline-item v-for="l in d.logs" :key="l.id" :timestamp="l.createTime?.slice(0, 16)" :type="logType(l.action)">
              <b>{{ MOC_LOG_ACTIONS[l.action] || l.action }}</b>　{{ l.operatorName }}
              <div class="muted">{{ l.content }}</div>
            </el-timeline-item>
          </el-timeline>
        </section>
      </template>
    </div>
    <template v-if="d && (can('EDIT') || can('SUBMIT') || can('CANCEL') || can('RESTORE'))" #footer>
      <el-button v-if="can('CANCEL')" type="danger" plain class="left" @click="act('cancel', '取消原因（可不填）', false)">取消变更</el-button>
      <el-button v-if="can('EDIT')" @click="emit('edit', c); visible = false">修改</el-button>
      <el-button v-if="can('SUBMIT')" type="primary" @click="act('submit', null, false)">提交风险评估</el-button>
      <el-button v-if="can('RESTORE')" type="warning" @click="act('restore', '恢复情况（已恢复原状的说明）', true)">登记已恢复原状</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mocAction, mocDetail, mocOptions } from '@/api'
import { MOC_LOG_ACTIONS, MOC_STATUS, MOC_STEPS, MOC_TYPES, RISK_GRADES } from '@/utils/dict'

const emit = defineEmits(['changed', 'edit'])
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const d = ref(null)
const opts = ref(null)
const c = computed(() => d.value?.change)
const todayStr = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const stepIndex = computed(() => {
  const i = MOC_STEPS.indexOf(c.value.status)
  return c.value.status === 'CLOSED' ? MOC_STEPS.length : i
})
const approvals = computed(() => (d.value?.logs || []).filter((l) => ['APPROVE', 'REJECT'].includes(l.action)))
const pssrRows = computed(() => d.value?.pssr || [])
const a = reactive({ lecL: null, lecE: null, lecC: null, riskAnalysis: '', measures: '' })
const p = reactive({ results: [], notes: [] })
const calc = computed(() => {
  if (a.lecL == null || a.lecE == null || a.lecC == null) return null
  const v = Math.round(a.lecL * a.lecE * a.lecC * 10) / 10
  return { d: v, level: v > 320 ? 'MAJOR' : v > 160 ? 'HIGH' : v > 70 ? 'GENERAL' : 'LOW' }
})
const can = (x) => d.value?.actions.includes(x)
const logType = (x) => (['REJECT', 'PSSR_FAIL', 'RETURN', 'CANCEL'].includes(x) ? 'danger' : ['PSSR_PASS', 'RESTORE'].includes(x) ? 'success' : 'primary')

let currentId = null
async function open(id) {
  currentId = id
  d.value = null
  visible.value = true
  if (!opts.value) opts.value = await mocOptions()
  await refresh(false)
}
async function refresh(notify = true) {
  loading.value = true
  try {
    d.value = await mocDetail(currentId)
    const x = d.value.change
    Object.assign(a, { lecL: x.lecL, lecE: x.lecE, lecC: x.lecC, riskAnalysis: x.riskAnalysis || '', measures: x.measures || '' })
    p.results = d.value.pssr.map(() => null)
    p.notes = d.value.pssr.map(() => '')
  } finally {
    loading.value = false
  }
  if (notify) emit('changed')
}
async function act(action, prompt, required) {
  let comment = ''
  if (prompt) {
    const r = await ElMessageBox.prompt(prompt, MOC_LOG_ACTIONS[action.toUpperCase()] || '确认', {
      inputType: 'textarea', inputValidator: (v) => !required || !!(v && v.trim()) || '请填写', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    comment = r.value || ''
  } else if (action === 'submit') {
    await ElMessageBox.confirm('提交后由安全管理员进行风险评估，评估期间不能修改。确定提交吗？', '提交', { type: 'info' })
  }
  await mocAction(currentId, action, { comment })
  ElMessage.success('已处理')
  await refresh()
}
async function assess() {
  if (!calc.value) return ElMessage.warning('请选择 L、E、C')
  if (!a.riskAnalysis.trim() || !a.measures.trim()) return ElMessage.warning('请填写风险分析和控制措施')
  saving.value = true
  try {
    await mocAction(currentId, 'assess', { ...a })
    ElMessage.success('评估完成，已通知审批人')
    await refresh()
  } finally {
    saving.value = false
  }
}
async function pssr() {
  saving.value = true
  try {
    await mocAction(currentId, 'pssr', { results: p.results, notes: p.notes })
    ElMessage.success(p.results.every((x) => x === true) ? '检查合格，变更已投用' : '有不合格项，已退回实施')
    await refresh()
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.wrap { min-height: 300px; }
.steps { margin-bottom: 18px; }
section { margin-top: 18px; }
h4 { margin: 0 0 10px; }
h4 .muted { font-weight: normal; margin-left: 6px; }
.pre { white-space: pre-wrap; }
.muted { color: #999; font-size: 12px; }
.lec { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.lec .el-select { width: 200px; }
.d { font-size: 13px; }
.grade { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; margin: 0 6px; }
.mt { margin-top: 8px; }
.risk { font-size: 14px; margin-bottom: 6px; }
.kv { display: flex; gap: 12px; font-size: 13px; margin: 4px 0; }
.kv span { color: #888; width: 64px; flex-shrink: 0; }
.kv p { margin: 0; line-height: 1.6; }
.appr { font-size: 13px; padding: 6px 10px; border-radius: 6px; margin-bottom: 6px; }
.appr.good { background: #f0f9eb; }
.appr.bad { background: #fef0f0; }
.left { float: left; }
</style>
