<template>
  <el-drawer v-model="visible" size="960px" destroy-on-close :title="inv ? inv.eventTitle : '事故调查'">
    <div v-loading="loading" class="wrap">
      <template v-if="d">
        <!-- 事故概况 -->
        <div class="head">
          <div class="tags">
            <el-tag :type="ACCIDENT_STATES[inv.state]?.type" effect="dark">{{ ACCIDENT_STATES[inv.state]?.label }}</el-tag>
            <el-tag :type="EMERGENCY_LEVELS[inv.level]?.type">{{ EMERGENCY_LEVELS[inv.level]?.label }}</el-tag>
            <el-tag type="info">{{ ACCIDENT_TYPES[inv.accidentType] || inv.accidentType }}</el-tag>
          </div>
          <div class="info">
            <span>编号 {{ inv.eventNo }}</span>
            <span>发生 {{ inv.occurTime?.slice(0, 16) }}</span>
            <span v-if="d.event">地点 {{ d.event.location }}</span>
            <span v-if="d.event">伤亡：{{ injury(d.event) }}</span>
          </div>
          <div v-if="d.event?.description" class="desc">{{ d.event.description }}</div>
          <div class="info">
            <span>调查组长 <b>{{ inv.leaderName }}</b></span>
            <span v-if="inv.members">成员 {{ inv.members }}</span>
            <span>调查期限 <b :class="{ danger: inv.state === 'OVERDUE' }">{{ inv.deadline }}</b></span>
            <el-button v-if="d.canClose" link type="primary" size="small" @click="openTeam">修改调查组</el-button>
          </div>
          <div v-if="inv.status === 'CLOSED'" class="closed">
            {{ inv.closeTime?.slice(0, 16) }} 由 {{ inv.closerName }} 结案<template v-if="inv.toKnowledge">，事故案例已写进 AI 知识库</template>
          </div>
        </div>

        <!-- 四不放过 -->
        <div class="four">
          <div v-for="c in d.checks" :key="c.key" class="check" :class="{ ok: c.ok }">
            <div class="ck-title"><el-icon><component :is="c.ok ? 'CircleCheckFilled' : 'WarningFilled'" /></el-icon>{{ c.label }}</div>
            <div class="ck-hint">{{ c.hint }}</div>
          </div>
        </div>

        <!-- 1. 原因分析 -->
        <section>
          <h4>原因分析</h4>
          <el-form label-width="100px" :disabled="!d.canEdit">
            <el-form-item label="直接原因" required><el-input v-model="a.directCause" type="textarea" :rows="2" maxlength="1000" placeholder="人的不安全行为、物的不安全状态" /></el-form-item>
            <el-form-item label="间接原因"><el-input v-model="a.indirectCause" type="textarea" :rows="2" maxlength="1000" placeholder="技术、设计、教育培训等方面的原因" /></el-form-item>
            <el-form-item label="管理原因" required><el-input v-model="a.managementCause" type="textarea" :rows="2" maxlength="1000" placeholder="制度、审批、监护、培训等管理上的缺陷" /></el-form-item>
            <el-form-item label="直接经济损失">
              <el-input-number v-model="a.economicLoss" :min="0" :max="100000" :precision="2" :controls="false" style="width: 140px" /> <span class="unit">万元</span>
            </el-form-item>
            <el-form-item label="事故教训"><el-input v-model="a.lessons" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
            <el-form-item v-if="d.canEdit"><el-button type="primary" :loading="saving === 'a'" @click="saveAnalysis">保存原因分析</el-button></el-form-item>
          </el-form>
        </section>

        <!-- 2. 整改措施 -->
        <section>
          <h4>整改措施 <el-button v-if="d.canEdit" size="small" icon="Plus" @click="openMeasure">新增措施</el-button></h4>
          <el-table :data="d.measures" size="small" border>
            <el-table-column type="index" width="44" />
            <el-table-column prop="content" label="措施" min-width="240" />
            <el-table-column prop="ownerName" label="责任人" width="130" show-overflow-tooltip />
            <el-table-column prop="deadline" label="期限" width="100" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="ACCIDENT_MEASURE_STATES[row.state]?.type">{{ ACCIDENT_MEASURE_STATES[row.state]?.label }}</el-tag></template>
            </el-table-column>
            <el-table-column label="落实情况" min-width="170">
              <template #default="{ row }">
                <template v-if="row.doneNote">{{ row.doneNote }}<div class="muted">{{ row.doneBy }} · {{ row.doneTime?.slice(0, 10) }}</div></template>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column v-if="inv.status !== 'CLOSED'" label="操作" width="120">
              <template #default="{ row }">
                <template v-if="row.status === 'OPEN'">
                  <el-button v-if="row.ownerId === myId || canAdmin" link type="primary" @click="doneRef.open(row)">完成</el-button>
                  <el-button v-if="d.canEdit" link type="danger" @click="removeMeasure(row)">删除</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 3. 责任处理 -->
        <section>
          <h4>责任人员处理</h4>
          <el-table :data="d.punishes" size="small" border>
            <el-table-column prop="personName" label="责任人员" width="180" />
            <el-table-column label="责任类别" width="100"><template #default="{ row }">{{ ACCIDENT_DUTIES[row.duty] }}</template></el-table-column>
            <el-table-column prop="measure" label="处理措施" min-width="260" />
            <el-table-column v-if="d.canEdit" label="操作" width="70">
              <template #default="{ row }"><el-button link type="danger" @click="removePunish(row)">删除</el-button></template>
            </el-table-column>
          </el-table>
          <div v-if="d.canEdit" class="inline">
            <el-input v-model="p.personName" placeholder="责任人员（姓名或岗位）" maxlength="50" style="width: 190px" />
            <el-select v-model="p.duty" placeholder="责任类别" style="width: 110px">
              <el-option v-for="(v, k) in ACCIDENT_DUTIES" :key="k" :label="v" :value="k" />
            </el-select>
            <el-input v-model="p.measure" placeholder="处理措施，如通报批评、经济处罚、停岗培训" maxlength="200" style="flex: 1" />
            <el-button :loading="saving === 'p'" @click="addPunish">添加</el-button>
          </div>
        </section>

        <!-- 4. 警示教育 -->
        <section>
          <h4>警示教育</h4>
          <el-form label-width="100px" :disabled="!d.canEdit" class="edu">
            <el-form-item label="教育日期" required>
              <el-date-picker v-model="e.educationDate" type="date" value-format="YYYY-MM-DD" :popper-options="PICKER_POPPER" />
            </el-form-item>
            <el-form-item label="参加人数" required><el-input-number v-model="e.educationCount" :min="1" :max="10000" /></el-form-item>
            <el-form-item label="教育内容" class="full"><el-input v-model="e.educationNote" type="textarea" :rows="2" maxlength="500" placeholder="例如：全厂班组长以上召开事故警示会，通报事故经过和原因，学习受限空间作业规程" /></el-form-item>
            <el-form-item v-if="d.canEdit" class="full"><el-button type="primary" :loading="saving === 'e'" @click="saveEducation">保存警示教育</el-button></el-form-item>
          </el-form>
        </section>
      </template>
    </div>

    <template v-if="d?.canClose" #footer>
      <el-checkbox v-model="toKnowledge">结案后把事故案例写进 AI 知识库（以后问 AI 能查到本企业的事故教训）</el-checkbox>
      <el-button type="success" :disabled="!allOk" :loading="saving === 'c'" @click="close">{{ allOk ? '结案' : '“四不放过”未满足，不能结案' }}</el-button>
    </template>

    <el-dialog v-model="mVisible" title="新增整改措施" width="560px" append-to-body>
      <el-form ref="mFormRef" :model="m" :rules="mRules" label-width="80px">
        <el-form-item label="措施" prop="content"><el-input v-model="m.content" type="textarea" :rows="3" maxlength="300" show-word-limit /></el-form-item>
        <el-form-item label="责任人" prop="ownerId">
          <el-select v-model="m.ownerId" filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" /></el-select>
        </el-form-item>
        <el-form-item label="期限" prop="deadline">
          <el-date-picker v-model="m.deadline" type="date" value-format="YYYY-MM-DD" :disabled-date="(x) => x < today" :popper-options="PICKER_POPPER" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving === 'm'" @click="addMeasure">保存并通知责任人</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tVisible" title="修改调查组" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="调查组长" required>
          <el-select v-model="t.leaderId" filterable style="width: 100%"><el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" /></el-select>
        </el-form-item>
        <el-form-item label="成员"><el-input v-model="t.members" maxlength="200" /></el-form-item>
        <el-form-item label="调查期限"><el-date-picker v-model="t.deadline" type="date" value-format="YYYY-MM-DD" :popper-options="PICKER_POPPER" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving === 't'" @click="saveTeam">保存</el-button>
      </template>
    </el-dialog>

    <DoneDialog ref="doneRef" @done="refresh" />
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  accidentDetail, addAccidentMeasure, addAccidentPunish, closeAccident, removeAccidentMeasure, removeAccidentPunish,
  saveAccidentAnalysis, saveAccidentEducation, saveAccidentTeam, userOptions
} from '@/api'
import { ACCIDENT_DUTIES, ACCIDENT_MEASURE_STATES, ACCIDENT_STATES, ACCIDENT_TYPES, EMERGENCY_LEVELS } from '@/utils/dict'
import { PICKER_POPPER } from '@/utils/picker'
import DoneDialog from './DoneDialog.vue'

const emit = defineEmits(['changed'])
const store = useUserStore()
const canAdmin = computed(() => store.hasPerm('accident:edit'))
const myId = computed(() => String(store.user?.id ?? ''))
const visible = ref(false)
const loading = ref(false)
const saving = ref('')
const d = ref(null)
const inv = computed(() => d.value?.investigation)
const allOk = computed(() => d.value?.checks.every((c) => c.ok))
const users = ref([])
const doneRef = ref()
const toKnowledge = ref(true)
const now = new Date()
const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())

const a = reactive({ directCause: '', indirectCause: '', managementCause: '', economicLoss: null, lessons: '' })
const e = reactive({ educationDate: null, educationCount: null, educationNote: '' })
const p = reactive({ personName: '', duty: 'DIRECT', measure: '' })
const m = reactive({ content: '', ownerId: null, deadline: null })
const t = reactive({ leaderId: null, members: '', deadline: null })
const mVisible = ref(false)
const tVisible = ref(false)
const mFormRef = ref()
const mRules = {
  content: [{ required: true, message: '请填写措施' }],
  ownerId: [{ required: true, message: '请选择责任人' }],
  deadline: [{ required: true, message: '请选择期限' }]
}
const injury = (ev) => {
  const parts = []
  if (ev.dead) parts.push(`死亡 ${ev.dead}`)
  if (ev.seriousInjured) parts.push(`重伤 ${ev.seriousInjured}`)
  if (ev.minorInjured) parts.push(`轻伤 ${ev.minorInjured}`)
  return parts.join('、') || '无'
}

let currentId = null
async function open(id) {
  currentId = id
  d.value = null
  visible.value = true
  toKnowledge.value = true
  await refresh(false)
  if (d.value?.canEdit && !users.value.length) users.value = await userOptions()
}
async function refresh(notify = true) {
  loading.value = true
  try {
    d.value = await accidentDetail(currentId)
    const v = d.value.investigation
    Object.assign(a, { directCause: v.directCause || '', indirectCause: v.indirectCause || '', managementCause: v.managementCause || '', economicLoss: v.economicLoss == null ? null : Number(v.economicLoss), lessons: v.lessons || '' })
    Object.assign(e, { educationDate: v.educationDate, educationCount: v.educationCount, educationNote: v.educationNote || '' })
  } finally {
    loading.value = false
  }
  if (notify) emit('changed')
}
async function run(key, fn, msg) {
  saving.value = key
  try {
    await fn()
    if (msg) ElMessage.success(msg)
    await refresh()
    return true
  } finally {
    saving.value = ''
  }
}
const saveAnalysis = () => run('a', () => saveAccidentAnalysis(currentId, { ...a }), '原因分析已保存')
function saveEducation() {
  if (!e.educationDate || !e.educationCount) return ElMessage.warning('请填写教育日期和参加人数')
  return run('e', () => saveAccidentEducation(currentId, { ...e }), '警示教育已保存')
}
async function addPunish() {
  if (!p.personName.trim() || !p.measure.trim()) return ElMessage.warning('请填写责任人员和处理措施')
  if (await run('p', () => addAccidentPunish(currentId, { ...p }), '已添加')) Object.assign(p, { personName: '', measure: '' })
}
async function removePunish(row) {
  await ElMessageBox.confirm(`删除对“${row.personName}”的处理记录？`, '删除', { type: 'warning' })
  await run('', () => removeAccidentPunish(row.id), '已删除')
}
function openMeasure() {
  Object.assign(m, { content: '', ownerId: null, deadline: null })
  mVisible.value = true
  mFormRef.value?.clearValidate()
}
async function addMeasure() {
  await mFormRef.value.validate()
  if (await run('m', () => addAccidentMeasure(currentId, { ...m }), '已添加，已通知责任人')) mVisible.value = false
}
async function removeMeasure(row) {
  await ElMessageBox.confirm('删除这项整改措施？', '删除', { type: 'warning' })
  await run('', () => removeAccidentMeasure(row.id), '已删除')
}
async function openTeam() {
  Object.assign(t, { leaderId: inv.value.leaderId, members: inv.value.members || '', deadline: inv.value.deadline })
  if (!users.value.length) users.value = await userOptions()
  tVisible.value = true
}
async function saveTeam() {
  if (await run('t', () => saveAccidentTeam(currentId, { ...t }), '已保存')) tVisible.value = false
}
async function close() {
  await ElMessageBox.confirm('结案后调查内容不能再修改。确定结案吗？', '结案', { type: 'warning' })
  await run('c', () => closeAccident(currentId, toKnowledge.value), toKnowledge.value ? '已结案，事故案例正在写进 AI 知识库' : '已结案')
}
defineExpose({ open })
</script>

<style scoped>
.wrap { min-height: 300px; }
.head { background: #f6f8fa; border-radius: 8px; padding: 12px 16px; }
.tags { display: flex; gap: 6px; margin-bottom: 8px; }
.info { display: flex; flex-wrap: wrap; gap: 4px 20px; font-size: 13px; color: #555; margin: 4px 0; align-items: center; }
.desc { font-size: 13px; color: #333; line-height: 1.7; margin: 6px 0; }
.closed { margin-top: 6px; color: #67c23a; font-size: 13px; }
.danger { color: #f56c6c; }
.four { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 14px 0 4px; }
.check { border: 1px solid #fde2e2; background: #fef0f0; border-radius: 8px; padding: 10px 12px; }
.check.ok { border-color: #e1f3d8; background: #f0f9eb; }
.ck-title { display: flex; align-items: center; gap: 4px; font-weight: 600; font-size: 13px; color: #f56c6c; }
.check.ok .ck-title { color: #67c23a; }
.ck-hint { font-size: 12px; color: #666; margin-top: 4px; }
section { margin-top: 18px; }
h4 { margin: 0 0 10px; display: flex; align-items: center; gap: 10px; }
.muted { color: #999; font-size: 12px; }
.unit { margin-left: 6px; color: #666; }
.inline { display: flex; gap: 8px; margin-top: 8px; }
.edu { display: flex; flex-wrap: wrap; }
.edu .full { width: 100%; }
@media (max-width: 900px) { .four { grid-template-columns: repeat(2, 1fr); } }
</style>
