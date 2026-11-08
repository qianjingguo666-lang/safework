<template>
  <el-drawer :model-value="modelValue" size="min(980px, 100vw)" :title="d ? `${d.jsa.jsaNo} ${d.jsa.title}` : 'JSA'" @update:model-value="emit('update:modelValue', $event)" @open="load">
    <div v-loading="loading">
      <template v-if="d">
        <div class="head">
          <el-tag :type="JSA_STATUS[j.status].type" effect="dark">{{ JSA_STATUS[j.status].label }}</el-tag>
          <el-tag type="info">{{ j.ticketType ? TICKET_TYPES[j.ticketType]?.label : '通用' }}</el-tag>
          <el-tag :color="RISK_LEVELS[d.maxLevel].color" effect="dark" class="risk">最高 {{ RISK_LEVELS[d.maxLevel].label }}（{{ j.maxRisk }}）</el-tag>
          <el-tag v-if="j.aiDrafted" type="success" effect="plain">AI 起草</el-tag>
          <el-tag v-if="d.reviewDue" type="danger">发布超过 1 年，需要复审</el-tag>
        </div>
        <el-alert v-if="j.status === 'REJECTED'" type="error" :closable="false" show-icon :title="`被驳回：${j.reviewComment}`" class="mb" />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="作业内容" :span="2">{{ j.workContent }}</el-descriptions-item>
          <el-descriptions-item label="作业地点">{{ j.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="防护用品">{{ j.ppe || '-' }}</el-descriptions-item>
          <el-descriptions-item label="编写人">{{ j.authorName }}</el-descriptions-item>
          <el-descriptions-item label="审核">{{ j.reviewerName ? `${j.reviewerName} ${j.reviewTime?.slice(0, 10)}` : '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="d.actions.length" class="actions">
          <el-button v-if="has('EDIT')" icon="Edit" @click="emit('edit', d)">修改</el-button>
          <el-button v-if="has('SUBMIT')" type="primary" @click="act('submit', '提交审核？审核通过后才能在作业票里引用。', '已提交，等审核')">提交审核</el-button>
          <el-button v-if="has('APPROVE')" type="success" @click="act('approve', '审核通过并发布？', '已发布')">审核通过</el-button>
          <el-button v-if="has('REJECT')" type="danger" plain @click="reject">驳回</el-button>
          <el-button v-if="has('COPY')" icon="CopyDocument" @click="copy">{{ d.reviewDue ? '复制一份复审' : '复制' }}</el-button>
          <el-button v-if="has('ARCHIVE')" plain @click="act('archive', '停用后开作业票不能再引用这份 JSA，确定？', '已停用')">停用</el-button>
          <el-button v-if="has('DELETE')" type="danger" link @click="remove">删除</el-button>
        </div>

        <h4>作业步骤和风险控制（{{ d.steps.length }} 步）</h4>
        <StepTable :steps="d.steps" />

        <h4>引用的作业票 <span class="muted">{{ d.tickets.length }} 张（最近 20 张）</span></h4>
        <div v-if="!d.tickets.length" class="muted">还没有作业票引用</div>
        <div v-for="t in d.tickets" :key="t.id" class="tk">{{ t.ticketNo }} {{ t.title }} <span class="muted">{{ t.planStartTime?.slice(0, 16) }}</span></div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteJsa, jsaAction, jsaDetail } from '@/api'
import { TICKET_TYPES } from '@/utils/dict'
import StepTable from './StepTable.vue'
import { JSA_STATUS, RISK_LEVELS } from './jsa'

const props = defineProps({ modelValue: Boolean, id: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue', 'changed', 'edit', 'open'])
const d = ref(null)
const loading = ref(false)
const j = computed(() => d.value?.jsa || {})
const has = (a) => d.value?.actions.includes(a)

async function load() {
  if (!props.id) return
  loading.value = true
  try {
    d.value = await jsaDetail(props.id)
  } finally {
    loading.value = false
  }
}
async function act(action, confirm, msg) {
  await ElMessageBox.confirm(confirm, '确认')
  await jsaAction(j.value.id, action)
  ElMessage.success(msg)
  await load()
  emit('changed')
}
async function reject() {
  const { value } = await ElMessageBox.prompt('驳回原因（编写人会收到）', '驳回', { inputValidator: (v) => (v && v.trim() ? true : '请写原因') })
  await jsaAction(j.value.id, 'reject', { comment: value })
  ElMessage.success('已驳回')
  await load()
  emit('changed')
}
async function copy() {
  const id = await jsaAction(j.value.id, 'copy')
  ElMessage.success(d.value.reviewDue ? '已复制成草稿：修改后提交审核，发布后旧版本自动停用' : '已复制成草稿')
  emit('changed')
  emit('open', id)
}
async function remove() {
  await ElMessageBox.confirm('删除这份 JSA？', '删除', { type: 'warning' })
  await deleteJsa(j.value.id)
  ElMessage.success('已删除')
  emit('update:modelValue', false)
  emit('changed')
}
defineExpose({ load })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.risk { border: none; }
.mb { margin-bottom: 10px; }
.actions { margin: 12px 0; display: flex; gap: 8px; flex-wrap: wrap; }
h4 { margin: 18px 0 8px; }
.muted { color: #999; font-size: 12px; font-weight: normal; }
.tk { padding: 4px 0; font-size: 13px; }
</style>
