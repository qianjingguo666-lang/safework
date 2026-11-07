<template>
  <el-drawer :model-value="modelValue" title="工伤详情" size="640px" @update:model-value="emit('update:modelValue', $event)" @open="load">
    <div v-loading="loading">
      <template v-if="d">
        <div class="head">
          <el-tag :type="INJURY_STATUS[c.status]?.type" effect="dark">{{ INJURY_STATUS[c.status]?.label }}</el-tag>
          <el-tag v-if="c.employerType === 'CONTRACTOR'" type="warning">承包商人员</el-tag>
          <span class="muted">{{ c.caseNo }}</span>
        </div>

        <el-steps :active="stepActive" finish-status="success" simple class="mb">
          <el-step title="登记" />
          <el-step title="申请认定" />
          <el-step :title="c.status === 'NOT_RECOGNIZED' ? '不予认定' : '认定结果'" :status="c.status === 'NOT_RECOGNIZED' ? 'error' : undefined" />
          <el-step title="劳动能力鉴定" />
          <el-step title="结案" />
        </el-steps>

        <el-alert v-if="d.deadline.state === 'OVERDUE'" type="error" :closable="false" show-icon class="mb"
          :title="`工伤认定申请已超期 ${-d.deadline.daysLeft} 天（应在 ${d.deadline.dueDate} 前申请）`"
          description="超期这段时间发生的工伤待遇等费用由单位负担，请尽快申请。职工本人或近亲属在受伤 1 年内也可以直接申请。" />
        <el-alert v-else-if="d.deadline.state !== 'DONE'" :type="d.deadline.state === 'SOON' ? 'warning' : 'info'" :closable="false" show-icon class="mb"
          :title="`请在 ${d.deadline.dueDate} 前申请工伤认定（还剩 ${d.deadline.daysLeft} 天）`"
          :description="c.employerType === 'CONTRACTOR' ? `承包商人员由 ${c.employerName} 申请，请督促并登记申请日期` : '材料：工伤认定申请表、劳动关系证明、医疗诊断证明'" />

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="受伤人员">{{ c.injuredName }}</el-descriptions-item>
          <el-descriptions-item label="单位 / 部门">{{ c.employerType === 'CONTRACTOR' ? c.employerName : c.deptName || '本企业' }}</el-descriptions-item>
          <el-descriptions-item label="受伤时间">{{ c.injuryTime?.slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ c.location }}</el-descriptions-item>
          <el-descriptions-item label="伤情" :span="2">{{ [c.injuryPart, c.injuryNature].filter(Boolean).join(' ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经过" :span="2">{{ c.description }}</el-descriptions-item>
          <el-descriptions-item v-if="c.eventNo" label="关联事故" :span="2">{{ c.eventNo }}</el-descriptions-item>
          <el-descriptions-item v-if="c.applyDate" label="申请认定" :span="2">{{ c.applyDate }} {{ c.applyNo || '' }}</el-descriptions-item>
          <el-descriptions-item v-if="c.decisionDate" label="认定结果" :span="2">
            {{ c.decisionDate }} {{ c.decisionNo || '' }} {{ c.decisionNote ? '· ' + c.decisionNote : '' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="c.grade != null" label="伤残等级">{{ d.gradeLabel }}（{{ c.appraiseDate }}）</el-descriptions-item>
          <el-descriptions-item v-if="c.grade != null" label="本人工资">{{ c.monthlyWage == null ? '-' : money(c.monthlyWage) + ' 元/月' }}</el-descriptions-item>
          <el-descriptions-item v-if="d.lumpSumMonths" label="一次性伤残补助金" :span="2">
            {{ d.lumpSumMonths }} 个月本人工资{{ d.lumpSum != null ? ` = ${money(d.lumpSum)} 元` : '' }}<span class="muted">（工伤保险基金支付，参考）</span>
          </el-descriptions-item>
          <el-descriptions-item label="停工留薪期" :span="2">
            <template v-if="c.leaveStart">{{ c.leaveStart }} ~ {{ c.leaveEnd }}{{ c.leaveExtended ? '（已延长）' : '' }}{{ c.returnDate ? '，' + c.returnDate + ' 复工' : '' }}</template>
            <span v-else class="muted">未设置</span>
            <el-button v-if="canEdit && !['CLOSED', 'NOT_RECOGNIZED'].includes(c.status)" link type="primary" class="ml" @click="openLeave">设置</el-button>
          </el-descriptions-item>
          <el-descriptions-item v-if="c.closeDate" label="结案" :span="2">{{ c.closeDate }} {{ c.closeNote || '' }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ c.recorderName }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="canEdit && (d.actions.length || c.status === 'REPORTED')" class="actions">
          <el-button v-if="c.status === 'REPORTED'" icon="Edit" @click="emit('edit', c)">修改</el-button>
          <el-button v-if="d.actions.includes('APPLY')" type="primary" @click="openAct('apply')">登记已申请认定</el-button>
          <el-button v-if="d.actions.includes('RECOGNIZE')" type="primary" @click="openAct('decide')">登记认定结果</el-button>
          <el-button v-if="d.actions.includes('APPRAISE')" type="primary" @click="openAct('appraise')">登记劳动能力鉴定</el-button>
          <el-button v-if="d.actions.includes('CLOSE')" type="success" @click="openAct('close')">结案</el-button>
        </div>

        <div class="sec">
          <b>费用台账</b>
          <span class="muted">基金 {{ money(d.fundTotal) }} 元 · 单位 {{ money(d.companyTotal) }} 元</span>
          <div class="spacer" />
          <el-button v-if="canEdit && c.status !== 'CLOSED'" size="small" icon="Plus" @click="openCost">登记费用</el-button>
        </div>
        <el-table :data="d.costs" size="small" stripe empty-text="还没有费用记录">
          <el-table-column prop="payDate" label="日期" width="100" />
          <el-table-column label="类别" width="130"><template #default="{ row }">{{ COST_CATEGORIES[row.category] }}</template></el-table-column>
          <el-table-column label="金额（元）" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          <el-table-column label="支付方" width="110"><template #default="{ row }"><el-tag size="small" :type="row.payer === 'FUND' ? 'success' : 'warning'">{{ PAYERS[row.payer] }}</el-tag></template></el-table-column>
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column v-if="canEdit && c.status !== 'CLOSED'" width="60">
            <template #default="{ row }"><el-button link type="danger" @click="delCost(row)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <el-dialog v-model="act.visible" :title="ACT_TITLES[act.type]" width="460px" append-to-body>
      <el-form :model="act.f" label-width="100px">
        <template v-if="act.type === 'apply'">
          <el-form-item label="申请日期" required><el-date-picker v-model="act.f.applyDate" value-format="YYYY-MM-DD" :disabled-date="future" /></el-form-item>
          <el-form-item label="受理编号"><el-input v-model="act.f.applyNo" maxlength="40" /></el-form-item>
        </template>
        <template v-if="act.type === 'decide'">
          <el-form-item label="认定结果" required>
            <el-radio-group v-model="act.f.recognized"><el-radio :value="true">认定为工伤</el-radio><el-radio :value="false">不予认定</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item label="认定日期" required><el-date-picker v-model="act.f.decisionDate" value-format="YYYY-MM-DD" :disabled-date="future" /></el-form-item>
          <el-form-item label="决定书编号"><el-input v-model="act.f.decisionNo" maxlength="40" /></el-form-item>
          <el-form-item label="说明"><el-input v-model="act.f.note" type="textarea" maxlength="200" /></el-form-item>
        </template>
        <template v-if="act.type === 'appraise'">
          <el-form-item label="伤残等级" required>
            <el-select v-model="act.f.grade" style="width: 200px"><el-option v-for="g in GRADES" :key="g.value" :label="g.label" :value="g.value" /></el-select>
          </el-form-item>
          <el-form-item label="鉴定日期" required><el-date-picker v-model="act.f.appraiseDate" value-format="YYYY-MM-DD" :disabled-date="future" /></el-form-item>
          <el-form-item label="本人月工资"><el-input-number v-model="act.f.monthlyWage" :min="0" :precision="2" :controls="false" style="width: 160px" /> 元</el-form-item>
          <div class="muted tip">一次性伤残补助金：一级 27 个月本人工资，逐级递减到十级 7 个月，由工伤保险基金支付。</div>
        </template>
        <template v-if="act.type === 'close'">
          <el-form-item label="结案说明"><el-input v-model="act.f.note" type="textarea" maxlength="200" /></el-form-item>
        </template>
        <template v-if="act.type === 'leave'">
          <el-form-item label="停工留薪期" required>
            <el-date-picker v-model="act.f.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" />
          </el-form-item>
          <el-form-item label="延长"><el-checkbox v-model="act.f.extended">经劳动能力鉴定委员会确认延长（最长 24 个月）</el-checkbox></el-form-item>
          <el-form-item label="复工日期"><el-date-picker v-model="act.f.returnDate" value-format="YYYY-MM-DD" /></el-form-item>
          <div class="muted tip">停工留薪期一般不超过 12 个月，期间原工资福利待遇不变，由单位按月支付。</div>
        </template>
        <template v-if="act.type === 'cost'">
          <el-form-item label="类别" required>
            <el-select v-model="act.f.category" style="width: 200px"><el-option v-for="(v, k) in COST_CATEGORIES" :key="k" :label="v" :value="k" /></el-select>
          </el-form-item>
          <el-form-item label="金额（元）" required><el-input-number v-model="act.f.amount" :min="0.01" :precision="2" :controls="false" style="width: 160px" /></el-form-item>
          <el-form-item label="支付方" required>
            <el-radio-group v-model="act.f.payer"><el-radio v-for="(v, k) in PAYERS" :key="k" :value="k">{{ v }}</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item label="支付日期" required><el-date-picker v-model="act.f.payDate" value-format="YYYY-MM-DD" :disabled-date="future" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="act.f.remark" maxlength="100" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="act.visible = false">取消</el-button>
        <el-button type="primary" :loading="act.saving" @click="submitAct">确定</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { addInjuryCost, injuryAction, injuryDetail, injuryLeave, removeInjuryCost } from '@/api'
import { COST_CATEGORIES, GRADES, INJURY_STATUS, money, PAYERS, today } from './injury'

const props = defineProps({ modelValue: Boolean, id: { type: [Number, String], default: null } })
const emit = defineEmits(['update:modelValue', 'changed', 'edit'])
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('injury:edit'))
const ACT_TITLES = { apply: '登记已申请工伤认定', decide: '登记认定结果', appraise: '登记劳动能力鉴定', close: '结案', leave: '停工留薪期', cost: '登记费用' }
const STEP = { REPORTED: 1, APPLIED: 2, RECOGNIZED: 3, NOT_RECOGNIZED: 3, APPRAISED: 4, CLOSED: 5 }
const d = ref(null)
const loading = ref(false)
const c = computed(() => d.value?.injury || {})
const stepActive = computed(() => STEP[c.value.status] || 0)
const act = reactive({ visible: false, type: '', f: {}, saving: false })
const future = (x) => x > new Date()

async function load() {
  if (!props.id) return
  loading.value = true
  try {
    d.value = await injuryDetail(props.id)
  } finally {
    loading.value = false
  }
}
function openAct(type) {
  const defaults = {
    apply: { applyDate: today(), applyNo: '' },
    decide: { recognized: true, decisionDate: today(), decisionNo: '', note: '' },
    appraise: { grade: 10, appraiseDate: today(), monthlyWage: null },
    close: { note: '' }
  }
  Object.assign(act, { visible: true, type, f: defaults[type] })
}
function openLeave() {
  const x = c.value
  Object.assign(act, { visible: true, type: 'leave', f: { range: x.leaveStart ? [x.leaveStart, x.leaveEnd] : [x.injuryTime?.slice(0, 10), null], extended: !!x.leaveExtended, returnDate: x.returnDate } })
}
function openCost() {
  Object.assign(act, { visible: true, type: 'cost', f: { category: 'MEDICAL', amount: null, payer: 'COMPANY', payDate: today(), remark: '' } })
}
async function submitAct() {
  act.saving = true
  try {
    const f = act.f
    if (act.type === 'leave') {
      if (!f.range?.[0] || !f.range?.[1]) return ElMessage.warning('请选择停工留薪期')
      await injuryLeave(props.id, { leaveStart: f.range[0], leaveEnd: f.range[1], extended: f.extended, returnDate: f.returnDate })
    } else if (act.type === 'cost') {
      await addInjuryCost(props.id, f)
    } else {
      await injuryAction(props.id, act.type, f)
    }
    ElMessage.success('已保存')
    act.visible = false
    await load()
    emit('changed')
  } finally {
    act.saving = false
  }
}
async function delCost(row) {
  await ElMessageBox.confirm(`删除这笔 ${COST_CATEGORIES[row.category]} ${money(row.amount)} 元？`, '删除费用', { type: 'warning' })
  await removeInjuryCost(row.id)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.muted { color: #999; font-size: 12px; }
.mb { margin-bottom: 12px; }
.ml { margin-left: 8px; }
.tip { margin: -6px 0 8px 100px; line-height: 1.6; }
.actions { margin: 12px 0; display: flex; gap: 8px; flex-wrap: wrap; }
.sec { display: flex; align-items: center; gap: 10px; margin: 18px 0 8px; }
.spacer { flex: 1; }
</style>
