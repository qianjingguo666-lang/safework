<template>
  <el-dialog v-model="visible" :title="editId ? '修改作业票' : '新建作业票'" width="720px" top="5vh" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="作业类型" prop="ticketType">
        <el-radio-group v-model="form.ticketType" :disabled="!!editId">
          <el-radio-button v-for="(v, k) in TICKET_TYPES" :key="k" :value="k">{{ v.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="作业名称" prop="title">
        <el-input v-model="form.title" maxlength="100" placeholder="如：1# 储罐内部清理" />
      </el-form-item>
      <el-form-item v-if="form.ticketType === 'CONFINED_SPACE'" label="受限空间" prop="spaceId">
        <el-select v-model="form.spaceId" filterable placeholder="从受限空间台账中选择" style="width: 100%" @change="onSpace">
          <el-option v-for="sp in spaces" :key="sp.id" :label="`${sp.spaceCode}  ${sp.spaceName}（${sp.location}）`" :value="sp.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="作业地点" prop="location">
        <el-input v-model="form.location" placeholder="如：罐区 T-101" />
      </el-form-item>
      <el-form-item label="计划时间" prop="planRange">
        <el-date-picker
          v-model="form.planRange" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间" end-placeholder="结束时间" style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="监护人" prop="guardianId">
        <el-select v-model="form.guardianId" placeholder="请选择监护人" style="width: 100%">
          <el-option v-for="g in guardians" :key="g.id" :label="g.nickname" :value="g.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="作业内容">
        <el-input v-model="form.workContent" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="安全措施">
        <el-input v-model="form.safetyMeasures" type="textarea" :rows="3" placeholder="如：1. 切断物料并加盲板 2. 强制通风 3. 配备呼吸器" />
      </el-form-item>

      <template v-if="gasRequired">
        <el-divider content-position="left">气体检测（提交审批前必须填写且合格）</el-divider>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="氧含量">
              <el-input-number v-model="form.gasOxygen" :precision="2" :step="0.1" :min="0" :max="100" controls-position="right" />
              <span class="unit">%VOL（19.5~23.5）</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可燃气体">
              <el-input-number v-model="form.gasLel" :precision="2" :step="0.1" :min="0" :max="100" controls-position="right" />
              <span class="unit">%LEL（≤10）</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="硫化氢">
              <el-input-number v-model="form.gasH2s" :precision="2" :step="0.1" :min="0" controls-position="right" />
              <span class="unit">mg/m³（≤10）</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测时间">
              <el-date-picker v-model="form.gasCheckTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTicket, listGuardians, spaceOptions, updateTicket } from '@/api'
import { TICKET_TYPES } from '@/utils/dict'

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const editId = ref(null)
const formRef = ref()
const guardians = ref([])
const spaces = ref([])

const pad = (n) => String(n).padStart(2, '0')
const nowStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

const empty = () => ({
  ticketType: 'CONFINED_SPACE', spaceId: null, title: '', location: '', planRange: [], guardianId: null,
  workContent: '', safetyMeasures: '', gasOxygen: null, gasLel: null, gasH2s: null, gasCheckTime: nowStr()
})
const form = reactive(empty())
const gasRequired = computed(() => TICKET_TYPES[form.ticketType]?.gas)
const rules = {
  ticketType: [{ required: true, message: '请选择作业类型' }],
  title: [{ required: true, message: '请输入作业名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入作业地点', trigger: 'blur' }],
  planRange: [{ required: true, type: 'array', len: 2, message: '请选择计划时间' }],
  guardianId: [{ required: true, message: '请选择监护人' }],
  spaceId: [{ required: true, message: '受限空间作业必须选择台账中的空间' }]
}

// 选择受限空间后自动带出位置
function onSpace(id) {
  const sp = spaces.value.find((x) => x.id === id)
  if (sp) form.location = `${sp.location} ${sp.spaceName}`
}

async function open(ticket) {
  Object.assign(form, empty())
  editId.value = ticket?.id || null
  if (ticket) {
    Object.keys(form).forEach((k) => { if (k in ticket && ticket[k] != null) form[k] = ticket[k] })
    form.planRange = [ticket.planStartTime, ticket.planEndTime]
    // el-input-number 需要数字，后端 BigDecimal 可能返回字符串
    ;['gasOxygen', 'gasLel', 'gasH2s'].forEach((k) => { if (form[k] != null) form[k] = Number(form[k]) })
  }
  visible.value = true
  const [g, sp] = await Promise.all([listGuardians(), spaceOptions()])
  guardians.value = g
  spaces.value = sp
}

async function save() {
  await formRef.value.validate()
  const { planRange, ...rest } = form
  const data = { ...rest, planStartTime: planRange[0], planEndTime: planRange[1] }
  if (data.ticketType !== 'CONFINED_SPACE') data.spaceId = null
  if (!gasRequired.value) {
    data.gasOxygen = data.gasLel = data.gasH2s = data.gasCheckTime = null
  }
  saving.value = true
  try {
    let id = editId.value
    if (id) {
      await updateTicket(id, data)
    } else {
      id = await createTicket(data)
    }
    ElMessage.success('已保存为草稿，确认无误后请在详情中“提交审批”')
    visible.value = false
    emit('saved', id)
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.unit { margin-left: 8px; color: #999; font-size: 12px; white-space: nowrap; }
</style>
