<template>
  <div v-loading="loading">
    <div v-for="r in rows" :key="r.change.id" class="m-card" @click="toggle(r.change.id)">
      <div class="m-item-head">
        <span class="m-item-title">{{ r.change.title }}</span>
        <el-tag size="small" :type="MOC_STATUS[r.change.status]?.type" effect="dark">{{ MOC_STATUS[r.change.status]?.label }}</el-tag>
      </div>
      <div class="m-muted">{{ r.change.changeNo }} · {{ MOC_TYPES[r.change.changeType] }} · {{ r.change.applicantName }}</div>
      <div v-if="r.change.riskLevel" class="risk">
        <span class="grade" :style="{ background: RISK_GRADES[r.change.riskLevel].color, color: RISK_GRADES[r.change.riskLevel].text }">{{ RISK_GRADES[r.change.riskLevel].label }}</span>
        D = {{ r.change.lecD }}<template v-if="r.change.status === 'APPROVING'">　{{ r.approved }}/{{ r.change.approvalsNeeded }} 人已批</template>
      </div>
      <div v-if="r.restoreOverdue" class="bad">临时变更已超过恢复日期 {{ r.change.restoreDate }}</div>

      <div v-if="open === r.change.id && d" class="detail" @click.stop>
        <div class="kv"><span>变更内容</span><p>{{ d.change.content }}</p></div>
        <div v-if="d.change.riskAnalysis" class="kv"><span>风险分析</span><p>{{ d.change.riskAnalysis }}</p></div>
        <div v-if="d.change.measures" class="kv"><span>控制措施</span><p>{{ d.change.measures }}</p></div>
        <template v-if="d.actions.includes('APPROVE')">
          <el-input v-model="comment" type="textarea" :rows="2" placeholder="审批意见（驳回时必填）" />
          <div class="btns">
            <el-button type="danger" plain :loading="saving" @click="act('reject')">驳回</el-button>
            <el-button type="success" :loading="saving" @click="act('approve')">审批通过</el-button>
          </div>
        </template>
        <div v-else class="m-muted pc">这一步（{{ MOC_STATUS[d.change.status]?.label }}）请在电脑上的【安全管理 → 变更管理】里处理</div>
      </div>
    </div>
    <div v-if="!loading && !rows.length" class="m-card m-muted">没有要你处理的变更</div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { mocAction, mocDetail, mocPage } from '@/api'
import { MOC_STATUS, MOC_TYPES, RISK_GRADES } from '@/utils/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const open = ref(null)
const d = ref(null)
const comment = ref('')

async function load() {
  loading.value = true
  try {
    rows.value = (await mocPage({ todo: true, size: 50 })).records
  } finally {
    loading.value = false
  }
}
async function toggle(id) {
  if (open.value === id) {
    open.value = null
    return
  }
  open.value = id
  d.value = null
  comment.value = ''
  d.value = await mocDetail(id)
}
async function act(action) {
  if (action === 'reject' && !comment.value.trim()) return ElMessage.warning('请写明驳回原因')
  saving.value = true
  try {
    await mocAction(open.value, action, { comment: comment.value.trim() })
    ElMessage.success(action === 'approve' ? '已审批通过' : '已驳回')
    open.value = null
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.risk { font-size: 13px; margin-top: 6px; }
.grade { display: inline-block; padding: 1px 8px; border-radius: 4px; font-size: 12px; margin-right: 6px; }
.bad { color: #f5222d; font-size: 13px; margin-top: 4px; }
.detail { margin-top: 10px; padding-top: 10px; border-top: 1px solid #f2f3f5; }
.kv { font-size: 14px; margin-bottom: 8px; }
.kv span { color: #888; font-size: 12px; }
.kv p { margin: 2px 0 0; line-height: 1.6; white-space: pre-wrap; }
.btns { display: flex; gap: 8px; margin-top: 8px; }
.btns .el-button { flex: 1; }
.pc { margin-top: 6px; }
</style>
