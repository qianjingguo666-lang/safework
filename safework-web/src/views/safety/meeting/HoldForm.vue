<template>
  <div class="hold">
    <div class="row">
      <span class="lbl">日期</span>
      <el-date-picker v-model="form.meetingDate" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled-date="disabledDate"
                      :popper-options="PICKER_POPPER" style="width: 150px" />
      <el-radio-group v-model="form.shift" class="shift">
        <el-radio-button v-for="(v, k) in MEETING_SHIFTS" :key="k" :value="k">{{ v }}</el-radio-button>
      </el-radio-group>
    </div>

    <div class="row head">
      <span class="lbl">安全要点</span>
      <el-button size="small" type="primary" plain :loading="aiLoading" icon="MagicStick" @click="genTips">AI 根据今天的作业生成</el-button>
    </div>
    <div v-if="aiNote" class="note">{{ aiNote }}</div>
    <div v-if="aiWorks.length" class="note">今天的作业票：{{ aiWorks.join('；') }}</div>
    <el-input v-model="form.topics" type="textarea" :rows="6" maxlength="2000" show-word-limit
              placeholder="今天干什么活、有什么风险、要注意什么。可以先点上面的按钮让 AI 起草，再修改" />

    <div class="row head"><span class="lbl">点名（{{ presentCount }}/{{ form.roll.length }} 人出席）</span></div>
    <div v-for="r in form.roll" :key="r.userId" class="person" :class="{ bad: r.attend === 'PRESENT' && r.bodyState !== 'GOOD' }">
      <div class="name">{{ r.userName }}</div>
      <div class="opts">
        <el-radio-group v-model="r.attend" size="small">
          <el-radio-button v-for="(v, k) in MEETING_ATTENDS" :key="k" :value="k">{{ v.label }}</el-radio-button>
        </el-radio-group>
        <el-radio-group v-if="r.attend === 'PRESENT'" v-model="r.bodyState" size="small" class="cond">
          <el-radio-button v-for="(v, k) in MEETING_CONDITIONS" :key="k" :value="k">{{ v.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="r.attend === 'PRESENT' && r.bodyState !== 'GOOD'" class="warn">今天不能上岗，保存后会通知安全员安排顶岗</div>
    </div>

    <el-button type="primary" size="large" class="submit" :loading="saving" @click="submit">保存班前会</el-button>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { holdMeeting, meetingTips } from '@/api'
import { MEETING_ATTENDS, MEETING_CONDITIONS, MEETING_SHIFTS } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'

const props = defineProps({ team: { type: Object, required: true }, members: { type: Array, required: true } })
const emit = defineEmits(['saved'])
const pad = (n) => String(n).padStart(2, '0')
const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const now = new Date()
const hour = now.getHours()
const form = reactive({
  meetingDate: fmt(now),
  shift: hour >= 20 || hour < 4 ? 'NIGHT' : hour >= 14 ? 'MIDDLE' : 'DAY',
  topics: '',
  aiUsed: false,
  roll: props.members.map((m) => ({ userId: m.userId, userName: m.userName, attend: 'PRESENT', bodyState: 'GOOD' }))
})
const presentCount = computed(() => form.roll.filter((r) => r.attend === 'PRESENT').length)
const aiLoading = ref(false)
const aiNote = ref('')
const aiWorks = ref([])
const saving = ref(false)
const disabledDate = (d) => d > now || d < new Date(now.getTime() - 4 * 86400000)

async function genTips() {
  if (form.topics.trim()) await ElMessageBox.confirm('会覆盖已经写的安全要点，继续吗？', 'AI 生成', { type: 'info' })
  aiLoading.value = true
  try {
    const r = await meetingTips(props.team.id)
    form.topics = r.text
    form.aiUsed = r.aiUsed
    aiNote.value = r.note || ''
    aiWorks.value = r.works
  } finally {
    aiLoading.value = false
  }
}
async function submit() {
  if (form.topics.trim().length < 10) return ElMessage.warning('安全要点至少写 10 个字')
  saving.value = true
  try {
    const id = await holdMeeting({
      teamId: props.team.id, meetingDate: form.meetingDate, shift: form.shift, topics: form.topics, aiUsed: form.aiUsed,
      roll: form.roll.map((r) => ({ userId: r.userId, attend: r.attend, bodyState: r.attend === 'PRESENT' ? r.bodyState : null }))
    })
    const unfit = form.roll.filter((r) => r.attend === 'PRESENT' && r.bodyState !== 'GOOD').length
    ElMessage.success(unfit ? `已保存，${unfit} 人今天不能上岗，已通知安全员` : '已保存，已通知成员确认')
    emit('saved', id)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.row.head { justify-content: space-between; margin-top: 14px; }
.lbl { font-weight: 600; font-size: 14px; }
.shift { margin-left: 4px; }
.note { font-size: 12px; color: #999; margin-bottom: 6px; }
.person { border: 1px solid #ebeef5; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; }
.person.bad { border-color: #fbc4c4; background: #fef0f0; }
.name { font-weight: 500; margin-bottom: 6px; }
.opts { display: flex; gap: 8px; flex-wrap: wrap; }
.warn { color: #f56c6c; font-size: 12px; margin-top: 6px; }
.submit { width: 100%; margin-top: 10px; }
</style>
