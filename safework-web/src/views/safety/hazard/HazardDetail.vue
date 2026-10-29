<template>
  <el-drawer v-model="visible" size="600px" :title="h ? `${h.hazardNo}　${h.title}` : '隐患详情'">
    <div v-if="h" v-loading="loading">
      <div class="head">
        <el-tag :type="HAZARD_STATUS[h.status]?.type" size="large">{{ HAZARD_STATUS[h.status]?.label }}</el-tag>
        <el-tag :type="HAZARD_LEVELS[h.level]?.type" effect="dark">{{ HAZARD_LEVELS[h.level]?.label }}</el-tag>
        <el-tag v-if="h.overdue" type="danger" effect="dark">已逾期</el-tag>
      </div>

      <div v-if="actions.length" class="actions">
        <el-button v-for="a in actions" :key="a" :type="BTN[a] || 'default'" :loading="busy === a" @click="run(a)">{{ HAZARD_ACTIONS[a] }}</el-button>
      </div>

      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item label="类别">{{ h.category }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ h.location }}</el-descriptions-item>
        <el-descriptions-item label="关联空间" :span="2">{{ h.spaceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2"><span class="pre">{{ h.description || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="上报人">{{ h.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="上报时间">{{ h.createTime }}</el-descriptions-item>
        <el-descriptions-item label="整改人">{{ h.rectifierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="整改期限"><span :class="{ overdue: h.overdue }">{{ h.deadline || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="整改措施" :span="2"><span class="pre">{{ h.rectifyMeasure || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="验收人">{{ h.verifierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="验收时间">{{ h.verifyTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4>处理记录</h4>
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createTime" placement="top">
          <b>{{ HAZARD_ACTIONS[l.action] }}</b>　{{ l.operatorName }}
          <span v-if="l.toStatus" class="flow">{{ HAZARD_STATUS[l.fromStatus]?.label || '' }} → {{ HAZARD_STATUS[l.toStatus]?.label }}</span>
          <div v-if="l.comment" class="comment">{{ l.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-dialog v-model="assignVisible" title="指派整改" width="440px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="整改责任人" required>
          <el-select v-model="assign.rectifierId" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.deptName ? `${u.nickname}（${u.deptName}）` : u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改期限" required>
          <el-date-picker v-model="assign.deadline" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="整改要求"><el-input v-model="assign.comment" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'ASSIGN'" @click="doAssign">确定</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getHazard, hazardAction, userOptions } from '@/api'
import { HAZARD_ACTIONS, HAZARD_LEVELS, HAZARD_STATUS, nowStr } from '@/utils/dict'

const emit = defineEmits(['changed'])
const visible = ref(false)
const loading = ref(false)
const busy = ref('')
const h = ref(null)
const logs = ref([])
const actions = ref([])
const BTN = { ASSIGN: 'primary', RECTIFY: 'primary', VERIFY_PASS: 'success', VERIFY_FAIL: 'danger', REVOKE: 'info' }
const PATH = { ASSIGN: 'assign', REVOKE: 'revoke', RECTIFY: 'rectify', VERIFY_PASS: 'verify-pass', VERIFY_FAIL: 'verify-fail' }

async function open(id) {
  visible.value = true
  loading.value = true
  try {
    const d = await getHazard(id)
    h.value = d.hazard
    logs.value = d.logs
    actions.value = d.actions
  } finally {
    loading.value = false
  }
}

async function call(action, data) {
  busy.value = action
  try {
    await hazardAction(h.value.id, PATH[action], data)
    ElMessage.success(`${HAZARD_ACTIONS[action]}成功`)
    emit('changed')
    await open(h.value.id)
  } finally {
    busy.value = ''
  }
}

const assignVisible = ref(false)
const users = ref([])
const assign = reactive({ rectifierId: null, deadline: '', comment: '' })
async function doAssign() {
  if (!assign.rectifierId || !assign.deadline) {
    ElMessage.warning('请选择整改责任人和整改期限')
    return
  }
  await call('ASSIGN', { ...assign })
  assignVisible.value = false
}

async function run(action) {
  try {
    if (action === 'ASSIGN') {
      // 重大隐患默认 3 天，一般隐患默认 7 天
      Object.assign(assign, { rectifierId: null, deadline: nowStr(h.value.level === 'MAJOR' ? 3 : 7), comment: '' })
      assignVisible.value = true
      if (!users.value.length) users.value = await userOptions()
      return
    }
    if (action === 'VERIFY_PASS') {
      const { value } = await ElMessageBox.prompt('验收意见（可选）', '验收通过', { inputPlaceholder: '如：现场复查已整改到位' })
      return call(action, { comment: value })
    }
    const cfg = {
      RECTIFY: { title: '提交整改', tip: '请填写整改措施', key: 'rectifyMeasure' },
      VERIFY_FAIL: { title: '验收不通过', tip: '请填写不通过的原因', key: 'comment' },
      REVOKE: { title: '撤销隐患', tip: '请说明撤销原因（如：经核实不属于隐患）', key: 'comment' }
    }[action]
    const { value } = await ElMessageBox.prompt(cfg.tip, cfg.title, {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length > 0) || '不能为空'
    })
    return call(action, { [cfg.key]: value })
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') throw e
  }
}

defineExpose({ open })
</script>

<style scoped>
.head { display: flex; gap: 8px; align-items: center; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0; padding: 12px; background: #f6f8fb; border-radius: 6px; }
.block { margin-top: 16px; }
h4 { margin: 20px 0 10px; }
.pre { white-space: pre-wrap; }
.overdue { color: #f56c6c; font-weight: 600; }
.flow { color: #888; margin-left: 8px; font-size: 12px; }
.comment { margin-top: 4px; padding: 6px 10px; background: #f6f8fb; border-radius: 4px; color: #555; }
</style>
