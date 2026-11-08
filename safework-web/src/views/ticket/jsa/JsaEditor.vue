<template>
  <el-dialog :model-value="modelValue" :title="data ? '修改 JSA' : '编写 JSA'" width="min(1100px, 98vw)" top="4vh" @update:model-value="emit('update:modelValue', $event)" @open="init">
    <el-form ref="formRef" :model="f" :rules="rules" label-width="90px">
      <el-row :gutter="12">
        <el-col :xs="24" :span="12"><el-form-item label="名称" prop="title"><el-input v-model="f.title" maxlength="60" placeholder="如：污水调节池清淤作业 JSA" /></el-form-item></el-col>
        <el-col :xs="24" :span="12">
          <el-form-item label="作业类型">
            <el-select v-model="f.ticketType" clearable placeholder="不选是通用的" style="width: 100%">
              <el-option v-for="(v, k) in TICKET_TYPES" :key="k" :label="v.label" :value="k" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="作业内容" prop="workContent"><el-input v-model="f.workContent" type="textarea" :rows="2" maxlength="500" placeholder="做什么、怎么做，写得越具体 AI 起草得越准" /></el-form-item>
      <el-row :gutter="12">
        <el-col :xs="24" :span="12"><el-form-item label="作业地点"><el-input v-model="f.location" maxlength="100" /></el-form-item></el-col>
        <el-col :xs="24" :span="12"><el-form-item label="防护用品"><el-input v-model="f.ppe" maxlength="300" placeholder="用顿号分开" /></el-form-item></el-col>
      </el-row>
    </el-form>

    <div class="bar">
      <b>作业步骤</b>
      <span class="muted">{{ L_HINT }}；{{ S_HINT }}。重大风险（≥15）的控制措施至少 2 条，用分号或换行分开</span>
      <div class="spacer" />
      <el-button type="success" plain icon="MagicStick" :loading="drafting" @click="aiDraft">AI 起草</el-button>
      <el-button icon="Plus" @click="f.steps.push(blank())">加一步</el-button>
    </div>
    <el-alert v-if="note" :title="note" type="info" :closable="false" show-icon class="mb" />
    <div v-for="(s, i) in f.steps" :key="i" class="step" :style="{ borderLeftColor: RISK_LEVELS[riskLevel((s.likelihood || 0) * (s.severity || 0))].color }">
      <div class="s-head">
        <b>{{ i + 1 }}</b>
        <el-input v-model="s.step" maxlength="100" placeholder="作业步骤" class="s-name" />
        <span class="lbl">L</span><el-input-number v-model="s.likelihood" :min="1" :max="5" size="small" controls-position="right" class="num" />
        <span class="lbl">S</span><el-input-number v-model="s.severity" :min="1" :max="5" size="small" controls-position="right" class="num" />
        <el-tag :color="RISK_LEVELS[riskLevel(s.likelihood * s.severity)].color" effect="dark" class="risk">{{ s.likelihood * s.severity }} {{ RISK_LEVELS[riskLevel(s.likelihood * s.severity)].label }}</el-tag>
        <div class="spacer" />
        <el-button link icon="Top" :disabled="i === 0" @click="move(i, -1)" />
        <el-button link icon="Bottom" :disabled="i === f.steps.length - 1" @click="move(i, 1)" />
        <el-button link type="danger" icon="Delete" @click="f.steps.splice(i, 1)" />
      </div>
      <div class="s-body">
        <el-input v-model="s.hazards" type="textarea" :rows="2" maxlength="300" placeholder="这一步的危害和后果" />
        <el-input v-model="s.controls" type="textarea" :rows="2" maxlength="500" placeholder="控制措施（多条用分号或换行分开）" />
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { jsaAiDraft, saveJsa } from '@/api'
import { TICKET_TYPES } from '@/utils/dict'
import { L_HINT, RISK_LEVELS, riskLevel, S_HINT } from './jsa'

const props = defineProps({ modelValue: Boolean, data: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const saving = ref(false)
const drafting = ref(false)
const note = ref('')
const f = reactive({ steps: [] })
const rules = {
  title: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  workContent: [{ required: true, message: '请填写作业内容', trigger: 'blur' }]
}
const blank = () => ({ step: '', hazards: '', likelihood: 2, severity: 3, controls: '' })

function init() {
  const j = props.data?.jsa
  note.value = ''
  Object.assign(f, {
    title: j?.title || '', ticketType: j?.ticketType || '', workContent: j?.workContent || '', location: j?.location || '', ppe: j?.ppe || '',
    steps: (props.data?.steps || []).map((s) => ({ step: s.step, hazards: s.hazards, likelihood: s.likelihood, severity: s.severity, controls: s.controls })),
    aiDrafted: false
  })
  if (!f.steps.length) f.steps.push(blank(), blank())
  formRef.value?.clearValidate()
}
function move(i, d) {
  const [x] = f.steps.splice(i, 1)
  f.steps.splice(i + d, 0, x)
}
async function aiDraft() {
  await formRef.value.validate()
  if (f.steps.some((s) => s.step || s.controls)) {
    await ElMessageBox.confirm('AI 起草会替换现在的作业步骤，继续？', 'AI 起草')
  }
  drafting.value = true
  try {
    const r = await jsaAiDraft({ ticketType: f.ticketType, title: f.title, workContent: f.workContent, location: f.location })
    f.steps = r.steps.map((s) => ({ ...s }))
    if (r.ppe && !f.ppe) f.ppe = r.ppe
    f.aiDrafted = true
    note.value = (r.note || `AI 起草了 ${r.steps.length} 个步骤，请结合现场逐条核对、修改`) + (r.check ? `。注意：${r.check}` : '')
  } finally {
    drafting.value = false
  }
}
async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    const id = await saveJsa(props.data?.jsa?.id, { ...f })
    ElMessage.success('已保存草稿，确认无误后提交审核')
    emit('update:modelValue', false)
    emit('saved', id)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin: 4px 0 10px; }
.muted { color: #999; font-size: 12px; }
.mb { margin-bottom: 10px; }
.step { border: 1px solid #ebeef5; border-left: 4px solid #ccc; border-radius: 6px; padding: 8px 10px; margin-bottom: 8px; }
.s-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.s-name { flex: 1; min-width: 200px; }
.lbl { color: #888; font-size: 12px; }
.num { width: 76px; }
.risk { border: none; }
.s-body { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 6px; }
@media (max-width: 700px) { .s-body { grid-template-columns: 1fr; } }
</style>
