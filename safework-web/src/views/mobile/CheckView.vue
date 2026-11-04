<template>
  <div v-loading="loading">
    <template v-if="t">
      <div class="m-card">
        <div class="m-item-head">
          <span class="m-item-title big">{{ t.planName }}</span>
          <el-tag :type="CHECK_TASK_STATES[t.state]?.type" effect="dark">{{ CHECK_TASK_STATES[t.state]?.label }}</el-tag>
        </div>
        <div class="m-muted">{{ t.taskNo }} · 《{{ t.templateName }}》</div>
        <div class="m-muted">{{ t.location }} · 截止 {{ t.dueDate }}</div>
        <div v-if="t.status === 'DONE'" class="result" :class="t.failCount ? 'bad' : 'good'">
          {{ t.failCount ? `${t.failCount} 项不合格，转隐患 ${t.hazardCount} 条` : '全部合格' }}
          <span class="m-muted">　{{ t.finishTime }}</span>
        </div>
        <div v-else-if="!canSubmit" class="m-muted">等待检查人 {{ t.checkerName }} 检查</div>
      </div>

      <div v-if="canSubmit" class="progress">
        已检查 {{ checked }} / {{ items.length }}<template v-if="failCount">，<b>{{ failCount }} 项不合格</b></template>
        <button class="all-ok" @click="allOk">其余全部合格</button>
      </div>

      <div v-for="(it, i) in items" :key="i" class="m-card item" :class="cls(it, i)">
        <div class="content"><span class="no">{{ i + 1 }}</span>{{ it.content }}</div>
        <div v-if="it.standard" class="m-muted std">标准：{{ it.standard }}</div>

        <template v-if="t.status === 'DONE'">
          <div class="done" :class="it.ok ? 'good' : 'bad'">{{ it.ok ? '✓ 合格' : '✕ 不合格' }}</div>
          <div v-if="!it.ok && it.note" class="note">现场情况：{{ it.note }}</div>
          <router-link v-if="it.hazardId" :to="`/m/hazard/${it.hazardId}`" class="hz">已转隐患 {{ it.hazardNo }} ›</router-link>
        </template>

        <template v-else-if="canSubmit">
          <div class="btns">
            <button :class="{ on: form[i].ok === true }" class="ok" @click="form[i].ok = true">✓ 合格</button>
            <button :class="{ on: form[i].ok === false }" class="ng" @click="form[i].ok = false">✕ 不合格</button>
          </div>
          <div v-if="form[i].ok === false" class="fail">
            <el-input v-model="form[i].note" type="textarea" :rows="2" maxlength="200"
                      :placeholder="form[i].toHazard ? '现场情况（可选，会写进隐患描述）' : '没有转隐患时，写明现场是怎么处理的'" />
            <div class="fail-opts">
              <el-switch v-model="form[i].toHazard" active-text="转为隐患" />
              <div v-if="form[i].toHazard" class="chips">
                <button :class="{ on: form[i].level === 'GENERAL' }" @click="form[i].level = 'GENERAL'">一般</button>
                <button :class="{ on: form[i].level === 'MAJOR' }" class="major" @click="form[i].level = 'MAJOR'">重大</button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="m-card">
        <AttachmentPanel :key="`s-${t.id}-${t.status}`" biz-type="CHECK" :biz-id="t.id" tag="SCENE" title="现场照片" image-only
                         :empty-text="canSubmit ? '不合格的地方最好拍张照' : '没有现场照片'" />
      </div>
      <div v-if="canSubmit" class="m-card">
        <el-input v-model="summary" type="textarea" :rows="2" maxlength="500" placeholder="检查总结（可选）" />
      </div>
      <div v-else-if="t.summary" class="m-card"><h3>检查总结</h3>{{ t.summary }}</div>

      <div v-if="canSubmit" class="m-actions">
        <el-button type="primary" :loading="busy" @click="submit">提交检查结果（{{ checked }}/{{ items.length }}）</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkTaskDetail, submitCheckTask } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { CHECK_TASK_STATES } from '@/utils/dict'

const emit = defineEmits(['unread'])
const route = useRoute()
const loading = ref(false)
const busy = ref(false)
const t = ref(null)
const items = ref([])
const canSubmit = ref(false)
const form = ref([])
const summary = ref('')
const checked = computed(() => form.value.filter((f) => f.ok !== null).length)
const failCount = computed(() => form.value.filter((f) => f.ok === false).length)
const cls = (it, i) => (t.value.status === 'DONE' ? (it.ok ? '' : 'bad-card') : form.value[i]?.ok === false ? 'bad-card' : form.value[i]?.ok ? 'good-card' : '')

async function load() {
  loading.value = true
  try {
    const d = await checkTaskDetail(route.params.id)
    t.value = d.task
    items.value = d.items
    canSubmit.value = d.canSubmit
    form.value = d.items.map(() => ({ ok: null, note: '', toHazard: true, level: 'GENERAL' }))
  } finally {
    loading.value = false
  }
}
onMounted(load)

function allOk() {
  form.value.forEach((f) => { if (f.ok === null) f.ok = true })
}
async function submit() {
  const miss = form.value.findIndex((f) => f.ok === null)
  if (miss >= 0) return ElMessage.warning(`第 ${miss + 1} 项还没有检查`)
  const bad = form.value.findIndex((f) => f.ok === false && !f.toHazard && !f.note.trim())
  if (bad >= 0) return ElMessage.warning(`第 ${bad + 1} 项不合格：请转为隐患，或者写明现场处理情况`)
  const hazards = form.value.filter((f) => f.ok === false && f.toHazard).length
  if (hazards) {
    try {
      await ElMessageBox.confirm(`将生成 ${hazards} 条隐患并通知安全员，确定提交吗？`, '提交检查结果', { confirmButtonText: '提交', cancelButtonText: '再看看' })
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
    ElMessage.success(r.failCount ? `已提交，${r.failCount} 项不合格` : '已提交，全部合格')
    emit('unread')
    await load()
    window.scrollTo(0, 0)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.big { font-size: 17px; white-space: normal; }
.result { margin-top: 6px; font-weight: 600; }
.good { color: #52c41a; }
.bad { color: #f5222d; }
.progress { display: flex; align-items: center; gap: 6px; margin: 0 2px 10px; font-size: 14px; color: #555; }
.progress b { color: #f5222d; }
.all-ok { margin-left: auto; border: 1px solid #1f6feb; color: #1f6feb; background: #fff; border-radius: 14px; padding: 4px 12px; font-size: 13px; }
.item { border: 2px solid transparent; }
.good-card { border-color: #b7eb8f; }
.bad-card { border-color: #ffa39e; background: #fffafa; }
.content { font-size: 16px; line-height: 1.6; }
.no { display: inline-block; min-width: 22px; height: 22px; line-height: 22px; text-align: center; border-radius: 50%; background: #eef1f6; color: #555; font-size: 12px; margin-right: 6px; }
.std { margin: 4px 0 0 28px; }
.btns { display: flex; gap: 10px; margin-top: 10px; }
.btns button { flex: 1; height: 44px; border-radius: 8px; border: 1px solid #dcdfe6; background: #fff; font-size: 16px; color: #555; }
.btns .ok.on { background: #52c41a; border-color: #52c41a; color: #fff; }
.btns .ng.on { background: #f5222d; border-color: #f5222d; color: #fff; }
.fail { margin-top: 10px; }
.fail-opts { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.chips { display: flex; gap: 8px; }
.chips button { border: 1px solid #dcdfe6; background: #fff; color: #555; padding: 5px 14px; border-radius: 14px; font-size: 14px; }
.chips button.on { background: #fa8c16; border-color: #fa8c16; color: #fff; }
.chips button.major.on { background: #f5222d; border-color: #f5222d; }
.done { margin-top: 8px; font-weight: 600; }
.note { margin-top: 4px; font-size: 14px; color: #c45656; }
.hz { display: inline-block; margin-top: 4px; color: #f5222d; font-size: 14px; text-decoration: none; }
</style>
