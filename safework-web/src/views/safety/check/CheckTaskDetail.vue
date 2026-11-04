<template>
  <el-drawer v-model="visible" size="min(680px, 100vw)" :title="t ? `${t.taskNo}　${t.planName}` : '检查任务'">
    <div v-if="t" v-loading="loading">
      <div class="head">
        <el-tag size="large" :type="CHECK_TASK_STATES[t.state]?.type">{{ CHECK_TASK_STATES[t.state]?.label }}</el-tag>
        <span class="muted">{{ CHECK_TYPES[t.checkType] }} · 《{{ t.templateName }}》共 {{ t.itemCount }} 项</span>
      </div>
      <el-descriptions :column="2" border size="small" class="block">
        <el-descriptions-item label="检查区域" :span="2">{{ t.location }}</el-descriptions-item>
        <el-descriptions-item label="检查人">{{ t.checkerName }}</el-descriptions-item>
        <el-descriptions-item label="检查周期">{{ t.periodStart }} ~ {{ t.dueDate }}</el-descriptions-item>
        <template v-if="t.status === 'DONE'">
          <el-descriptions-item label="完成时间">{{ t.finishTime }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <span v-if="!t.failCount" class="ok">全部合格</span>
            <span v-else class="danger">{{ t.failCount }} 项不合格，转隐患 {{ t.hazardCount }} 条</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="t.summary" label="检查总结" :span="2">{{ t.summary }}</el-descriptions-item>
        </template>
      </el-descriptions>

      <el-alert v-if="!canSubmit && t.status !== 'DONE'" type="info" :closable="false" show-icon class="block"
                :title="`等待检查人 ${t.checkerName} 检查（手机上也可以检查）`" />

      <h4>检查项</h4>
      <div v-for="(it, i) in items" :key="i" class="item" :class="itemClass(it, i)">
        <div class="item-head">
          <span class="no">{{ i + 1 }}</span>
          <div class="content">
            <div>{{ it.content }}</div>
            <div v-if="it.standard" class="muted">标准：{{ it.standard }}</div>
          </div>
          <!-- 已完成：显示结果 -->
          <template v-if="t.status === 'DONE'">
            <el-tag v-if="it.ok" type="success" size="small">合格</el-tag>
            <el-tag v-else type="danger" size="small" effect="dark">不合格</el-tag>
          </template>
          <!-- 检查人：逐项选择 -->
          <el-radio-group v-else-if="canSubmit" v-model="form[i].ok" size="small">
            <el-radio-button :value="true">合格</el-radio-button>
            <el-radio-button :value="false">不合格</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="t.status === 'DONE' && !it.ok" class="fail-info">
          <span v-if="it.note">现场情况：{{ it.note }}</span>
          <span v-if="it.hazardNo">　已转隐患 <el-link type="danger" @click="$router.push(`/safety/hazard?detail=${it.hazardId}`)">{{ it.hazardNo }}</el-link></span>
        </div>
        <div v-if="canSubmit && form[i].ok === false" class="fail-form">
          <el-input v-model="form[i].note" size="small" maxlength="200" :placeholder="form[i].toHazard ? '现场情况（可选，会写进隐患描述）' : '没有转隐患时必须写明现场是怎么处理的'" />
          <div class="fail-opts">
            <el-checkbox v-model="form[i].toHazard">转为隐患（{{ it.category || '安全管理' }}）</el-checkbox>
            <el-radio-group v-if="form[i].toHazard" v-model="form[i].level" size="small">
              <el-radio value="GENERAL">一般隐患</el-radio>
              <el-radio value="MAJOR">重大隐患</el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>

      <AttachmentPanel :key="`s-${t.id}-${t.status}`" biz-type="CHECK" :biz-id="t.id" tag="SCENE" title="现场照片" image-only class="block"
                       :empty-text="canSubmit ? '可以上传现场照片，不合格项最好拍照' : '没有现场照片'" />

      <template v-if="canSubmit">
        <el-input v-model="summary" type="textarea" :rows="2" maxlength="500" placeholder="检查总结（可选）" class="block" />
        <div class="submit">
          <span class="muted">已检查 {{ checked }} / {{ items.length }} 项<template v-if="failCount">，{{ failCount }} 项不合格</template></span>
          <el-button size="small" @click="allOk">其余全部合格</el-button>
          <el-button type="primary" :loading="busy" @click="submit">提交检查结果</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkTaskDetail, submitCheckTask } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { CHECK_TASK_STATES, CHECK_TYPES } from '@/utils/dict'

const emit = defineEmits(['changed'])
const visible = ref(false)
const loading = ref(false)
const busy = ref(false)
const t = ref(null)
const items = ref([])
const canSubmit = ref(false)
const form = ref([])
const summary = ref('')
const checked = computed(() => form.value.filter((f) => f.ok !== null).length)
const failCount = computed(() => form.value.filter((f) => f.ok === false).length)
const itemClass = (it, i) => (t.value.status === 'DONE' ? (it.ok ? '' : 'bad') : form.value[i]?.ok === false ? 'bad' : form.value[i]?.ok ? 'good' : '')

async function open(id) {
  visible.value = true
  loading.value = true
  try {
    const d = await checkTaskDetail(id)
    t.value = d.task
    items.value = d.items
    canSubmit.value = d.canSubmit
    form.value = d.items.map(() => ({ ok: null, note: '', toHazard: true, level: 'GENERAL' }))
    summary.value = ''
  } finally {
    loading.value = false
  }
}
function allOk() {
  form.value.forEach((f) => { if (f.ok === null) f.ok = true })
}
async function submit() {
  const miss = form.value.findIndex((f) => f.ok === null)
  if (miss >= 0) return ElMessage.warning(`第 ${miss + 1} 项还没有检查`)
  const bad = form.value.findIndex((f) => f.ok === false && !f.toHazard && !f.note.trim())
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 项不合格：请转为隐患，或者写明现场是怎么处理的`)
  const hazards = form.value.filter((f) => f.ok === false && f.toHazard).length
  if (hazards) {
    try {
      await ElMessageBox.confirm(`将生成 ${hazards} 条隐患，安全员会收到通知。确定提交吗？`, '提交检查结果', { type: 'warning' })
    } catch {
      return
    }
  }
  busy.value = true
  try {
    const r = await submitCheckTask(t.value.id, {
      summary: summary.value.trim() || undefined,
      items: form.value.map((f) => ({ ok: f.ok, note: f.note.trim() || undefined, toHazard: f.ok === false && f.toHazard, level: f.level }))
    })
    ElMessage.success(r.failCount ? `已提交，${r.failCount} 项不合格${r.hazardNos.length ? `，已转隐患 ${r.hazardNos.join('、')}` : ''}` : '已提交，全部合格')
    emit('changed')
    await open(t.value.id)
  } finally {
    busy.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.block { margin-top: 12px; }
h4 { margin: 18px 0 8px; }
.muted { color: #999; font-size: 12px; }
.ok { color: #67c23a; }
.danger { color: #f56c6c; }
.item { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px; margin-bottom: 8px; }
.item.good { border-color: #b3e19d; background: #f6fbf3; }
.item.bad { border-color: #fab6b6; background: #fef5f5; }
.item-head { display: flex; align-items: flex-start; gap: 10px; }
.no { flex: none; width: 22px; height: 22px; border-radius: 50%; background: #eef1f6; color: #606266; font-size: 12px; display: flex; align-items: center; justify-content: center; }
.content { flex: 1; min-width: 0; line-height: 1.6; }
.fail-info { margin: 6px 0 0 32px; font-size: 13px; color: #c45656; }
.fail-form { margin: 8px 0 0 32px; }
.fail-opts { display: flex; align-items: center; gap: 16px; margin-top: 6px; flex-wrap: wrap; }
.submit { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.submit .muted { margin-right: auto; }
</style>
