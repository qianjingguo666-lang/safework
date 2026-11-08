<template>
  <div v-loading="loading">
    <template v-if="t">
      <div class="m-card">
        <div class="m-row"><span>{{ t.date }}</span>
          <el-tag v-if="committed" size="small" type="success" effect="dark">已承诺公告</el-tag>
          <el-tag v-else-if="!t.missing.length" size="small" type="warning">待主要负责人承诺</el-tag>
          <el-tag v-else size="small" type="info">还差 {{ t.missing.length }} 个方面</el-tag>
        </div>
        <div v-if="t.judgeLate || t.commitLate" class="late">{{ t.commitLate ? '已超过 10:00 还没承诺' : '已超过 9:00 还没研判完' }}</div>
      </div>
      <div v-for="a in t.aspects" :key="a.aspect" class="m-item">
        <div class="m-item-head">
          <span class="m-item-title">{{ a.label }}</span>
          <el-tag v-if="a.judgerName" size="small" :type="a.abnormal ? 'danger' : 'success'">{{ a.abnormal ? `${a.abnormal} 项异常` : '正常' }}</el-tag>
          <el-tag v-else size="small" type="info">未研判</el-tag>
        </div>
        <div v-if="a.judgerName" class="m-muted">{{ a.judgerName }} {{ a.judgeTime.slice(11, 16) }}</div>
        <div v-for="i in a.items.filter((x) => x.ok === false)" :key="i.code" class="abn">{{ i.text }}：{{ i.note }}；措施：{{ i.measure }}</div>
        <el-button v-if="t.canJudge" size="small" :type="a.judgerName ? 'default' : 'primary'" class="btn" @click="openJudge(a)">{{ a.judgerName ? '重新研判' : '研判' }}</el-button>
      </div>
      <div v-if="t.canSign" class="m-card">
        <b>主要负责人签字承诺</b>
        <SignaturePad ref="padRef" class="pad" placeholder="在此手写签名" />
        <div class="tools">
          <el-button @click="padRef.clear()">重写</el-button>
          <el-button type="primary" :loading="signing" @click="sign">签字承诺</el-button>
        </div>
      </div>
      <div v-if="board" class="board-wrap"><BoardCard :board="board" /></div>
    </template>
    <JudgeForm v-model="jVisible" :aspect="jAspect?.aspect" :view="jAspect" :commitment="t?.commitment" :last-units="t?.lastUnits" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commitBoard, commitSign, commitToday } from '@/api'
import SignaturePad from '@/components/SignaturePad.vue'
import BoardCard from '@/views/safety/commit/BoardCard.vue'
import JudgeForm from '@/views/safety/commit/JudgeForm.vue'

const t = ref(null)
const board = ref(null)
const loading = ref(false)
const committed = computed(() => t.value?.commitment?.status === 'COMMITTED')
async function load() {
  loading.value = true
  try {
    t.value = await commitToday()
    board.value = committed.value ? await commitBoard(t.value.date) : null
  } finally {
    loading.value = false
  }
}
const jVisible = ref(false)
const jAspect = ref(null)
function openJudge(a) {
  jAspect.value = a
  jVisible.value = true
}
const padRef = ref()
const signing = ref(false)
async function sign() {
  if (padRef.value.isEmpty()) return ElMessage.warning('请先手写签名')
  await ElMessageBox.confirm('签字后今天的研判就不能再改了，确认承诺并公告？', '承诺公告')
  signing.value = true
  try {
    await commitSign(padRef.value.toDataURL())
    ElMessage.success('已承诺公告')
    load()
  } finally {
    signing.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.late { color: #f56c6c; font-size: 13px; margin-top: 6px; }
.abn { color: #c0392b; font-size: 13px; margin-top: 4px; }
.btn { margin-top: 8px; width: 100%; }
.pad { margin-top: 8px; }
.tools { display: flex; gap: 8px; margin-top: 8px; }
.tools .el-button { flex: 1; }
.board-wrap { margin-top: 12px; }
</style>
