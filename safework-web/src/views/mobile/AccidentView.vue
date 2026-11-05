<template>
  <div v-loading="loading">
    <div v-for="m in rows" :key="m.id" class="m-card">
      <div class="m-item-head">
        <span class="m-muted">{{ m.eventTitle }}</span>
        <el-tag :type="ACCIDENT_MEASURE_STATES[m.state]?.type" :effect="m.state === 'OVERDUE' ? 'dark' : 'light'" size="small">{{ ACCIDENT_MEASURE_STATES[m.state]?.label }}</el-tag>
      </div>
      <div class="content">{{ m.content }}</div>
      <div class="m-muted">期限 {{ m.deadline }}</div>
      <div v-if="m.doneNote" class="done">落实情况：{{ m.doneNote }}</div>
      <template v-if="m.status === 'OPEN'">
        <template v-if="editing === m.id">
          <el-input v-model="note" type="textarea" :rows="3" maxlength="300" placeholder="做了什么、什么时候完成的" class="note" />
          <div class="btns">
            <el-button @click="editing = null">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit(m)">确认完成</el-button>
          </div>
        </template>
        <el-button v-else type="primary" plain class="full" @click="edit(m)">填写落实情况</el-button>
      </template>
    </div>
    <div v-if="!loading && !rows.length" class="m-card m-muted">没有分配给你的事故整改措施</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { doneAccidentMeasure, myAccidentMeasures } from '@/api'
import { ACCIDENT_MEASURE_STATES } from '@/utils/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const editing = ref(null)
const note = ref('')
async function load() {
  loading.value = true
  try {
    rows.value = await myAccidentMeasures()
  } finally {
    loading.value = false
  }
}
function edit(m) {
  editing.value = m.id
  note.value = ''
}
async function submit(m) {
  if (!note.value.trim()) return ElMessage.warning('请填写落实情况')
  saving.value = true
  try {
    await doneAccidentMeasure(m.id, note.value.trim())
    ElMessage.success('已完成')
    editing.value = null
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.content { font-size: 15px; line-height: 1.6; margin: 6px 0 4px; }
.done { font-size: 14px; color: #52c41a; margin-top: 4px; }
.note { margin-top: 8px; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btns .el-button { flex: 1; }
.full { width: 100%; margin-top: 8px; }
</style>
