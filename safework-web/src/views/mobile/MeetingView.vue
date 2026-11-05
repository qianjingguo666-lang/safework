<template>
  <div v-loading="loading">
    <template v-if="d">
      <div v-for="p in d.pending" :key="p.meeting.id" class="m-card pending">
        <div class="m-item-head">
          <span class="m-item-title">请确认：{{ p.meeting.teamName }}</span>
          <span class="m-muted">{{ p.meeting.meetingDate }} {{ MEETING_SHIFTS[p.meeting.shift] }}</span>
        </div>
        <div class="topics">{{ p.meeting.topics }}</div>
        <div v-if="p.me.bodyState && p.me.bodyState !== 'GOOD'" class="bad">班组长登记你今天“{{ MEETING_CONDITIONS[p.me.bodyState]?.label }}”，今天不上岗</div>
        <el-button type="primary" class="full" :loading="saving === p.meeting.id" @click="confirm(p.meeting.id)">我已参加，清楚今天的安全要点</el-button>
      </div>

      <div v-for="t in d.leading" :key="t.team.id" class="m-card">
        <div class="m-item-head">
          <span class="m-item-title">{{ t.team.name }}</span>
          <el-tag :type="t.today ? 'success' : 'warning'" effect="dark" size="small">{{ t.today ? '今天已开' : '今天未开' }}</el-tag>
        </div>
        <div class="m-muted">{{ t.members.length }} 人 · 最近 30 天开了 {{ t.held30 }} 天（{{ t.rate30 }}%）</div>
        <template v-if="holding === t.team.id">
          <HoldForm :team="t.team" :members="t.members" class="form" @saved="afterHold" />
          <el-button class="full" @click="holding = null">取消</el-button>
        </template>
        <el-button v-else type="primary" :plain="t.today" class="full" @click="holding = t.team.id">{{ t.today ? '再开一个班次' : '开班前会' }}</el-button>
      </div>

      <div v-if="savedId" class="m-card">
        <div class="m-item-title">上传现场照片（可选）</div>
        <AttachmentPanel :key="savedId" biz-type="MEETING" :biz-id="savedId" tag="PHOTO" title="" image-only empty-text="拍一张班前会现场照片" />
      </div>

      <div v-if="!d.leading.length && !d.pending.length" class="m-card m-muted">你不是班组长，也没有要确认的班前会</div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmMeeting, myMeetings } from '@/api'
import { MEETING_CONDITIONS, MEETING_SHIFTS } from '@/utils/dict'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import HoldForm from '@/views/safety/meeting/HoldForm.vue'

const loading = ref(false)
const saving = ref(null)
const d = ref(null)
const holding = ref(null)
const savedId = ref(null)

async function load() {
  loading.value = true
  try {
    d.value = await myMeetings()
  } finally {
    loading.value = false
  }
}
async function confirm(id) {
  saving.value = id
  try {
    await confirmMeeting(id)
    ElMessage.success('已确认')
    await load()
  } finally {
    saving.value = null
  }
}
async function afterHold(id) {
  holding.value = null
  savedId.value = id
  await load()
}
onMounted(load)
</script>

<style scoped>
.pending { border: 1px solid #ffd591; }
.topics { white-space: pre-wrap; font-size: 14px; line-height: 1.8; margin: 8px 0; }
.bad { color: #f5222d; font-size: 13px; margin-bottom: 8px; }
.full { width: 100%; margin-top: 8px; }
.form { margin-top: 10px; }
</style>
