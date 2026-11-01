<template>
  <el-drawer v-model="visible" size="min(640px, 100vw)" :title="e ? `${e.equipCode}　${e.name}` : '设备'">
    <div v-if="e" v-loading="loading">
      <div class="head">
        <el-tag size="large" :type="EQUIP_STATUS[e.status]?.type">{{ EQUIP_STATUS[e.status]?.label }}</el-tag>
        <template v-if="e.status === 'IN_USE'">
          <el-tag :type="EQUIP_STATES[e.inspectState]?.type" effect="dark">{{ EQUIP_STATES[e.inspectState]?.label }}</el-tag>
          <span v-if="e.daysLeft != null" class="muted">{{ e.daysLeft < 0 ? `逾期 ${-e.daysLeft} 天` : `距下次检验 ${e.daysLeft} 天` }}</span>
        </template>
      </div>
      <el-alert v-if="e.statusReason && e.status !== 'IN_USE'" type="warning" :closable="false" show-icon class="block" :title="e.statusReason" />

      <div v-if="canInspect || canEdit" class="actions">
        <el-button v-if="canInspect && e.status !== 'SCRAPPED'" type="primary" icon="Stamp" @click="openInspect">登记检验结果</el-button>
        <template v-if="canEdit && e.status !== 'SCRAPPED'">
          <el-button icon="Edit" @click="emit('edit', e)">修改</el-button>
          <el-button v-if="e.status === 'IN_USE'" @click="changeStatus('STOPPED')">停用</el-button>
          <el-button v-else type="success" @click="changeStatus('IN_USE')">重新启用</el-button>
          <el-button type="danger" plain @click="changeStatus('SCRAPPED')">报废</el-button>
        </template>
      </div>

      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item label="设备类型">{{ type?.label }}（{{ type?.categoryLabel }}）</el-descriptions-item>
        <el-descriptions-item label="检验周期">{{ e.cycleMonths }} 个月</el-descriptions-item>
        <el-descriptions-item label="位置" :span="2">{{ e.location }}</el-descriptions-item>
        <el-descriptions-item v-if="type?.special" label="使用登记证" :span="2">{{ e.registerNo }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ e.responsibleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联风险点">{{ e.riskPointName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上次检验">{{ e.lastInspectDate || '-' }}<el-tag v-if="e.lastResult" size="small" class="ml" :type="e.lastResult === 'PASS' ? 'success' : 'danger'">{{ e.lastResult === 'PASS' ? '合格' : '不合格' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="下次检验">{{ e.nextInspectDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="制造单位">{{ e.manufacturer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号规格">{{ e.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装日期">{{ e.installDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ e.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4>检验记录</h4>
      <el-empty v-if="!inspections.length" description="还没有检验记录" :image-size="60" />
      <el-timeline v-else>
        <el-timeline-item v-for="r in inspections" :key="r.id" :timestamp="r.inspectDate" placement="top" :type="r.result === 'PASS' ? 'success' : 'danger'">
          <b>{{ r.result === 'PASS' ? '合格' : '不合格' }}</b>
          <span class="muted">　{{ r.org || '' }} {{ r.reportNo ? `报告 ${r.reportNo}` : '' }}</span>
          <div v-if="r.conclusion" class="comment">{{ r.conclusion }}</div>
          <div class="muted">
            <span v-if="r.nextInspectDate">下次检验 {{ r.nextInspectDate }}　</span>
            <span v-if="r.hazardNo">已转隐患 <el-link type="danger" @click="$router.push(`/safety/hazard?detail=${r.hazardId}`)">{{ r.hazardNo }}</el-link>　</span>
            登记人 {{ r.recorderName }}
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-dialog v-model="inspectVisible" title="登记检验结果" width="min(520px, 94vw)" append-to-body>
      <el-form ref="inspectRef" :model="ins" :rules="inspectRules" label-width="100px">
        <el-form-item label="检验日期" prop="inspectDate">
          <el-date-picker v-model="ins.inspectDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(d) => d > new Date()" style="width: 100%" />
        </el-form-item>
        <el-form-item label="检验结论" prop="result">
          <el-radio-group v-model="ins.result">
            <el-radio value="PASS">合格</el-radio>
            <el-radio value="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验机构"><el-input v-model="ins.org" maxlength="60" placeholder="如：合肥市特种设备检验所" /></el-form-item>
        <el-form-item label="报告编号"><el-input v-model="ins.reportNo" maxlength="50" /></el-form-item>
        <el-form-item label="结论说明" :prop="ins.result === 'FAIL' ? 'conclusion' : ''">
          <el-input v-model="ins.conclusion" type="textarea" :rows="3" maxlength="500"
                    :placeholder="ins.result === 'FAIL' ? '写明不合格的原因，会写进隐患描述' : '可选'" />
        </el-form-item>
        <el-form-item v-if="ins.result === 'PASS'" label="下次检验">
          <el-date-picker v-model="ins.nextInspectDate" type="date" value-format="YYYY-MM-DD" :placeholder="`不填按周期：${defaultNext || '-'}`" style="width: 100%" />
        </el-form-item>
        <template v-else>
          <el-form-item label="上报隐患"><el-checkbox v-model="ins.toHazard">同时上报一条{{ hazardLevelText }}，进入整改闭环</el-checkbox></el-form-item>
          <el-alert type="warning" :closable="false" show-icon title="检验不合格的设备会自动停用，整改后重新检验合格才能启用" />
        </template>
      </el-form>
      <template #footer>
        <el-button @click="inspectVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="saveInspect">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { equipmentDetail, equipmentStatus, inspectEquipment } from '@/api'
import { EQUIP_STATES, EQUIP_STATUS } from '@/utils/dict'

const props = defineProps({ types: { type: Array, default: () => [] } })
const emit = defineEmits(['changed', 'edit'])
const store = useUserStore()
const canEdit = computed(() => store.hasPerm('equipment:edit'))
const canInspect = computed(() => store.hasPerm('equipment:inspect'))
const visible = ref(false)
const loading = ref(false)
const busy = ref(false)
const e = ref(null)
const inspections = ref([])
const type = computed(() => props.types.find((t) => t.value === e.value?.equipType))

async function open(id) {
  visible.value = true
  loading.value = true
  try {
    const d = await equipmentDetail(id)
    e.value = d.equipment
    inspections.value = d.inspections
  } finally {
    loading.value = false
  }
}
async function refresh() {
  emit('changed')
  await open(e.value.id)
}

async function changeStatus(to) {
  const label = EQUIP_STATUS[to].label
  let reason = ''
  if (to === 'IN_USE') {
    await ElMessageBox.confirm('确认设备已整改并检验合格，重新启用？', '重新启用', { type: 'warning' })
  } else {
    const r = await ElMessageBox.prompt(`请填写${label}原因${to === 'SCRAPPED' ? '（报废后不能恢复）' : ''}`, label, {
      inputPattern: /\S/, inputErrorMessage: '原因不能为空', inputType: 'textarea'
    })
    reason = r.value
  }
  await equipmentStatus(e.value.id, to, reason)
  ElMessage.success(`已${to === 'IN_USE' ? '启用' : label}`)
  refresh()
}

// ---------------- 检验 ----------------
const inspectVisible = ref(false)
const inspectRef = ref()
const ins = reactive({})
const inspectRules = {
  inspectDate: [{ required: true, message: '请填写检验日期' }],
  result: [{ required: true, message: '请选择检验结论' }],
  conclusion: [{ required: true, message: '请写明不合格的原因', trigger: 'blur' }]
}
const today = () => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return d.toISOString().slice(0, 10)
}
const defaultNext = computed(() => {
  if (!ins.inspectDate || !e.value) return ''
  const [y, m, d] = ins.inspectDate.split('-').map(Number)
  const t = new Date(y, m - 1 + e.value.cycleMonths, d)
  return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`
})
const hazardLevelText = computed(() => (['FIRE', 'OTHER'].includes(type.value?.category) ? '一般隐患' : '重大隐患'))
function openInspect() {
  Object.assign(ins, { inspectDate: today(), result: 'PASS', org: '', reportNo: '', conclusion: '', nextInspectDate: null, toHazard: true })
  inspectVisible.value = true
  inspectRef.value?.clearValidate()
}
async function saveInspect() {
  await inspectRef.value.validate()
  busy.value = true
  try {
    const data = { ...ins, toHazard: ins.result === 'FAIL' && ins.toHazard }
    if (ins.result === 'FAIL') data.nextInspectDate = null
    const r = await inspectEquipment(e.value.id, data)
    if (ins.result === 'PASS') ElMessage.success(`已登记，下次检验 ${r.nextInspectDate}`)
    else ElMessage.warning(`已登记。${r.stopped ? '设备已停用。' : ''}${r.hazardNo ? `已上报隐患 ${r.hazardNo}` : ''}`)
    inspectVisible.value = false
    refresh()
  } finally {
    busy.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.block { margin-top: 12px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; padding: 12px; background: #f6f8fb; border-radius: 6px; }
.actions .el-button { margin-left: 0; }
.muted { color: #999; font-size: 12px; }
.ml { margin-left: 6px; }
h4 { margin: 20px 0 10px; }
.comment { color: #555; margin-top: 4px; }
</style>
