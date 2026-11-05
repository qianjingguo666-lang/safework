<template>
  <el-dialog v-model="visible" title="启动事故调查" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="事故" prop="eventId">
        <el-select v-model="form.eventId" filterable placeholder="选择事故快报" style="width: 100%">
          <el-option v-for="c in candidates" :key="c.eventId" :value="c.eventId" :label="`${c.title}（${c.occurTime?.slice(0, 10)}）`">
            <div class="opt">
              <span>{{ c.title }}</span>
              <span class="muted">{{ EMERGENCY_LEVELS[c.level]?.label }} · {{ c.occurTime?.slice(0, 10) }}{{ injury(c) }}</span>
            </div>
          </el-option>
        </el-select>
        <div v-if="!candidates.length" class="muted">没有待调查的事故快报（事故快报在【应急事件】里上报）</div>
      </el-form-item>
      <el-form-item label="调查组长" prop="leaderId">
        <el-select v-model="form.leaderId" filterable style="width: 100%">
          <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
        </el-select>
        <div class="muted">组长能填写原因分析、整改措施、责任处理和警示教育</div>
      </el-form-item>
      <el-form-item label="调查组成员" prop="members"><el-input v-model="form.members" maxlength="200" placeholder="例如：设备部、生产部、承包商项目经理" /></el-form-item>
      <el-form-item label="调查期限" prop="deadline">
        <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" placeholder="不选按等级自动算" :disabled-date="(d) => d < today" :popper-options="PICKER_POPPER" />
        <div class="muted">不选：一般事故从事故发生起 30 天，较大及以上 60 天</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">启动调查</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { accidentCandidates, startAccident, userOptions } from '@/api'
import { EMERGENCY_LEVELS } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'

const emit = defineEmits(['started'])
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const candidates = ref([])
const users = ref([])
const now = new Date()
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
const form = reactive({ eventId: null, leaderId: null, members: '', deadline: null })
const rules = {
  eventId: [{ required: true, message: '请选择事故' }],
  leaderId: [{ required: true, message: '请选择调查组长' }]
}
const injury = (c) => {
  const parts = []
  if (c.dead) parts.push(`死亡 ${c.dead}`)
  if (c.seriousInjured) parts.push(`重伤 ${c.seriousInjured}`)
  if (c.minorInjured) parts.push(`轻伤 ${c.minorInjured}`)
  return parts.length ? ` · ${parts.join('、')}` : ' · 无伤亡'
}

async function open() {
  Object.assign(form, { eventId: null, leaderId: null, members: '', deadline: null })
  visible.value = true
  formRef.value?.clearValidate()
  const [c, u] = await Promise.all([accidentCandidates(), users.value.length ? users.value : userOptions()])
  candidates.value = c
  users.value = u
}
async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    const id = await startAccident({ ...form })
    ElMessage.success('已启动调查，已通知调查组长')
    visible.value = false
    emit('started', id)
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.opt { display: flex; justify-content: space-between; gap: 16px; }
.muted { color: #999; font-size: 12px; }
</style>
