<template>
  <el-dialog v-model="visible" title="填写落实情况" width="520px" append-to-body>
    <div v-if="m" class="content">{{ m.content }}<span class="muted">（期限 {{ m.deadline }}）</span></div>
    <el-input v-model="note" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="做了什么、什么时候完成的，例如：已安装 2 台固定式硫化氢报警仪并接入中控室，11 月 6 日调试合格" />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认完成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { doneAccidentMeasure } from '@/api'

const emit = defineEmits(['done'])
const visible = ref(false)
const saving = ref(false)
const m = ref(null)
const note = ref('')
function open(measure) {
  m.value = measure
  note.value = ''
  visible.value = true
}
async function submit() {
  if (!note.value.trim()) {
    ElMessage.warning('请填写落实情况')
    return
  }
  saving.value = true
  try {
    await doneAccidentMeasure(m.value.id, note.value.trim())
    ElMessage.success('已完成')
    visible.value = false
    emit('done')
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.content { margin-bottom: 10px; line-height: 1.6; }
.muted { color: #999; font-size: 12px; margin-left: 4px; }
</style>
