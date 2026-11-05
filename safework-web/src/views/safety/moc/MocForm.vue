<template>
  <el-dialog v-model="visible" :title="form.id ? '修改变更申请' : '申请变更'" width="680px" top="6vh">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="变更名称" prop="title"><el-input v-model="form.title" maxlength="60" placeholder="例如：T-103 储罐由储存甲醇改为储存乙醇" /></el-form-item>
      <el-form-item label="变更类型" prop="changeType">
        <el-select v-model="form.changeType" style="width: 200px"><el-option v-for="(v, k) in MOC_TYPES" :key="k" :label="v" :value="k" /></el-select>
      </el-form-item>
      <el-form-item label="期限">
        <el-radio-group v-model="form.temporary">
          <el-radio :value="false">永久变更</el-radio>
          <el-radio :value="true">临时变更</el-radio>
        </el-radio-group>
        <el-date-picker v-if="form.temporary" v-model="form.restoreDate" type="date" value-format="YYYY-MM-DD" placeholder="恢复日期" class="restore"
                        :disabled-date="(d) => d <= today" :popper-options="PICKER_POPPER" />
        <div class="muted">临时变更（如检修期间临时用备用设备）到期要恢复原状，最长 180 天</div>
      </el-form-item>
      <el-form-item label="地点 / 装置" prop="location"><el-input v-model="form.location" maxlength="100" /></el-form-item>
      <el-form-item label="变更原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      <el-form-item label="变更内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="改什么、怎么改、影响哪些设备和操作" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveMoc } from '@/api'
import { MOC_TYPES } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const now = new Date()
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
const form = reactive({ id: null, title: '', changeType: 'PROCESS', temporary: false, restoreDate: null, location: '', reason: '', content: '' })
const rules = {
  title: [{ required: true, message: '请填写变更名称' }],
  changeType: [{ required: true, message: '请选择变更类型' }],
  location: [{ required: true, message: '请填写地点' }],
  reason: [{ required: true, message: '请填写变更原因' }],
  content: [{ required: true, message: '请填写变更内容' }]
}
function open(c) {
  Object.assign(form, c
    ? { id: c.id, title: c.title, changeType: c.changeType, temporary: !!c.temporary, restoreDate: c.restoreDate, location: c.location, reason: c.reason, content: c.content }
    : { id: null, title: '', changeType: 'PROCESS', temporary: false, restoreDate: null, location: '', reason: '', content: '' })
  visible.value = true
  formRef.value?.clearValidate()
}
async function save() {
  await formRef.value.validate()
  if (form.temporary && !form.restoreDate) return ElMessage.warning('临时变更请选择恢复日期')
  saving.value = true
  try {
    const { id, ...data } = form
    if (!data.temporary) data.restoreDate = null
    const r = await saveMoc(id, data)
    ElMessage.success('已保存草稿，确认无误后点“提交”')
    visible.value = false
    emit('saved', id || r)
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.restore { margin-left: 12px; }
.muted { color: #999; font-size: 12px; width: 100%; }
</style>
