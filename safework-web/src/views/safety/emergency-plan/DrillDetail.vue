<template>
  <el-drawer :model-value="modelValue" title="应急演练" size="min(720px, 100vw)" @update:model-value="emit('update:modelValue', $event)" @open="load">
    <div v-loading="loading">
      <template v-if="d">
        <div class="head">
          <b>{{ x.title }}</b>
          <el-tag size="small" :type="DRILL_STATUS[x.status]?.type" effect="dark">{{ DRILL_STATUS[x.status]?.label }}</el-tag>
          <el-tag size="small" type="info">{{ DRILL_TYPES[x.drillType] }}</el-tag>
          <el-tag v-if="x.grade" size="small" :type="DRILL_GRADE_TYPES[x.grade]">{{ x.totalScore }} 分 · {{ x.grade }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="预案" :span="2">{{ d.planCode }} {{ x.planTitle }}（{{ PLAN_TYPES[d.planType] }}）</el-descriptions-item>
          <el-descriptions-item label="计划时间">{{ x.planTime?.slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ x.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="演练情景" :span="2">{{ x.scenario || '-' }}</el-descriptions-item>
          <el-descriptions-item label="安排人">{{ x.organizerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际时间">{{ x.actualTime?.slice(0, 16) || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="d.mine" class="mine">
          你在参演名单里（{{ DRILL_ROLES[d.mine.role] }}）：
          <span v-if="d.mine.signTime" class="ok">已签到 {{ d.mine.signTime.slice(11, 16) }}</span>
          <el-button v-else-if="d.canSign" type="primary" size="small" @click="sign">签到</el-button>
          <span v-else class="muted">演练当天开始前 2 小时起可以签到</span>
        </div>

        <div v-if="d.actions.length" class="actions">
          <el-button v-if="has('EDIT')" icon="Edit" @click="emit('edit', d)">修改安排</el-button>
          <el-button v-if="has('COMPLETE')" type="primary" @click="openComplete">登记演练结果</el-button>
          <el-button v-if="has('EVALUATE')" type="primary" @click="openEvaluate">评估打分</el-button>
          <el-button v-if="has('CANCEL')" type="danger" plain @click="cancel">取消演练</el-button>
        </div>

        <h4>参演人员 <span class="muted">{{ d.participants.length }} 人，已签到 {{ d.participants.filter((p) => p.signTime).length }} 人</span></h4>
        <el-table :data="d.participants" size="small" stripe empty-text="没有指定参演人员（只通知了应急小组）">
          <el-table-column prop="userName" label="姓名" min-width="140" />
          <el-table-column prop="deptName" label="部门" width="110" />
          <el-table-column label="角色" width="100"><template #default="{ row }">{{ DRILL_ROLES[row.role] }}</template></el-table-column>
          <el-table-column label="签到" width="110"><template #default="{ row }">{{ row.signTime ? row.signTime.slice(5, 16) : '-' }}</template></el-table-column>
          <el-table-column label="实到" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.attended === 1" size="small" type="success">参加</el-tag>
              <el-tag v-else-if="row.attended === 0" size="small" type="danger">缺席</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="x.actualTime">
          <h4>演练结果 <span class="muted">参演 {{ x.participantCount }} 人 · {{ x.recorderName }} 登记</span></h4>
          <div class="pre box">{{ x.summary }}</div>
          <div v-if="x.problems" class="pre box warn">现场记录的问题：{{ x.problems }}</div>
        </template>

        <template v-if="x.status === 'EVALUATED'">
          <h4>评估（AQ/T 9009） <span class="muted">{{ x.evaluatorName }} {{ x.evaluateTime?.slice(0, 10) }}</span></h4>
          <el-table :data="d.scores" size="small" border show-summary :summary-method="sumScores">
            <el-table-column prop="name" label="评估项目" width="140" />
            <el-table-column label="得分 / 满分" width="110" align="center">
              <template #default="{ row }">{{ row.score == null ? '不适用' : `${row.score} / ${row.max}` }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="评语" min-width="200" />
          </el-table>
          <el-alert :type="x.planSuitable ? 'success' : 'warning'" :closable="false" show-icon class="mt"
                    :title="x.planSuitable ? '预案适宜，不需要修订' : '预案需要修订：' + x.reviseNote" />
          <h4>发现的问题</h4>
          <div v-if="!d.problems.length" class="muted">没有发现问题</div>
          <div v-for="p in d.problems" :key="p.problem.id" class="problem">
            <el-tag size="small" type="info">{{ DRILL_PROBLEM_CATEGORIES[p.problem.category] }}</el-tag>
            <span class="desc">{{ p.problem.description }}</span>
            <template v-if="p.problem.hazardId">
              <router-link :to="`/safety/hazard?detail=${p.problem.hazardId}`" class="link">{{ p.problem.hazardNo }}</router-link>
              <el-tag size="small" :type="p.hazardStatus === 'CLOSED' ? 'success' : 'warning'">{{ p.hazardStatusLabel }}</el-tag>
            </template>
            <el-button v-else-if="has('TO_HAZARD')" size="small" type="warning" plain @click="toHazard(p.problem)">转隐患整改</el-button>
          </div>
        </template>

        <h4>现场照片</h4>
        <AttachmentPanel :key="x.id + '-' + x.status" :biz-type="BIZ" :biz-id="x.id" tag="PHOTO" title="现场照片" image-only empty-text="还没有照片" />
      </template>
    </div>

    <!-- 登记结果 -->
    <el-dialog v-model="cVisible" title="登记演练结果" width="min(560px, 94vw)" append-to-body>
      <el-form :model="c" label-width="90px">
        <el-form-item label="实际时间" required>
          <el-date-picker v-model="c.actualTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" format="YYYY-MM-DD HH:mm" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="d?.participants.length" label="实到人员">
          <el-checkbox-group v-model="c.attendedUserIds">
            <el-checkbox v-for="p in d.participants" :key="p.userId" :value="String(p.userId)">{{ p.userName }}{{ p.signTime ? '（已签到）' : '' }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="参演人数" required>
          <el-input-number v-model="c.participantCount" :min="Math.max(1, c.attendedUserIds.length)" :max="9999" />
          <span class="muted ml">含名单以外的人员</span>
        </el-form-item>
        <el-form-item label="演练总结" required><el-input v-model="c.summary" type="textarea" :rows="4" maxlength="2000" /></el-form-item>
        <el-form-item label="现场问题"><el-input v-model="c.problems" type="textarea" :rows="2" maxlength="2000" placeholder="先简单记下，评估时再逐条整理" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveComplete">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评估 -->
    <el-dialog v-model="eVisible" title="演练评估" width="min(760px, 96vw)" append-to-body>
      <div class="muted mb">按 AQ/T 9009《生产安全事故应急演练评估规范》逐项打分（最小 0.5 分）；桌面推演不涉及的项目选“不适用”，总分按适用项目折算成百分制。</div>
      <el-table :data="e.scores" size="small" border>
        <el-table-column label="评估项目" min-width="200">
          <template #default="{ row }"><b>{{ row.name }}</b><div class="muted">{{ row.hint }}</div></template>
        </el-table-column>
        <el-table-column label="得分" width="190">
          <template #default="{ row }">
            <el-checkbox v-model="row.na" @change="(v) => { if (!v && row.score == null) row.score = row.max }">不适用</el-checkbox>
            <el-input-number v-if="!row.na" v-model="row.score" :min="0" :max="row.max" :step="0.5" :precision="1" size="small" style="width: 100px" />
            <span v-if="!row.na" class="muted"> / {{ row.max }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评语" min-width="160">
          <template #default="{ row }"><el-input v-model="row.remark" size="small" maxlength="200" /></template>
        </el-table-column>
      </el-table>
      <div class="total">折算总分：<b>{{ preview.total }}</b> 分 <el-tag size="small" :type="DRILL_GRADE_TYPES[preview.grade]">{{ preview.grade }}</el-tag></div>
      <el-form :model="e" label-width="100px">
        <el-form-item label="预案是否适宜">
          <el-radio-group v-model="e.planSuitable"><el-radio :value="true">适宜</el-radio><el-radio :value="false">需要修订</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="!e.planSuitable" label="修订意见" required>
          <el-input v-model="e.reviseNote" type="textarea" :rows="2" maxlength="300" placeholder="会提醒预案管理员修改预案" />
        </el-form-item>
        <el-form-item label="发现的问题">
          <div class="ps">
            <div v-for="(p, i) in e.problems" :key="i" class="p-row">
              <el-select v-model="p.category" style="width: 120px"><el-option v-for="(v, k) in DRILL_PROBLEM_CATEGORIES" :key="k" :label="v" :value="k" /></el-select>
              <el-input v-model="p.description" maxlength="300" placeholder="问题描述" />
              <el-button link type="danger" icon="Delete" @click="e.problems.splice(i, 1)" />
            </div>
            <el-button size="small" icon="Plus" @click="e.problems.push({ category: 'EQUIPMENT', description: '' })">添加问题</el-button>
            <span class="muted ml">评估后可以逐条转隐患整改</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEvaluate">提交评估</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelEmergencyDrill, completeEmergencyDrill, drillDetail, drillProblemToHazard, evaluateDrill, signDrill } from '@/api'
import AttachmentPanel from '@/components/AttachmentPanel.vue'
import { DRILL_GRADE_TYPES, DRILL_PROBLEM_CATEGORIES, DRILL_ROLES, DRILL_STATUS, DRILL_TYPES, PLAN_TYPES } from '@/utils/dict'

const props = defineProps({ modelValue: Boolean, id: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue', 'changed', 'edit'])
const BIZ = 'EMERGENCY_DRILL'
const d = ref(null)
const loading = ref(false)
const saving = ref(false)
const x = computed(() => d.value?.drill || {})
const has = (a) => d.value?.actions.includes(a)

async function load() {
  if (!props.id) return
  loading.value = true
  try {
    d.value = await drillDetail(props.id)
  } finally {
    loading.value = false
  }
}
async function changed(msg) {
  ElMessage.success(msg)
  await load()
  emit('changed')
}
async function sign() {
  await signDrill(x.value.id)
  changed('已签到')
}
async function cancel() {
  await ElMessageBox.confirm(`取消演练「${x.value.title}」？参演人员会收到通知。`, '取消演练', { type: 'warning' })
  await cancelEmergencyDrill(x.value.id)
  changed('已取消')
}
async function toHazard(p) {
  await ElMessageBox.confirm(`把“${p.description}”转为隐患，走指派、整改、验收流程？`, '转隐患整改')
  await drillProblemToHazard(p.id)
  changed('已转隐患，到隐患管理里指派整改')
}
function sumScores({ data }) {
  const got = data.filter((r) => r.score != null)
  const s = got.reduce((t, r) => t + Number(r.score), 0)
  const m = got.reduce((t, r) => t + r.max, 0)
  return ['合计', `${s} / ${m}`, `折算 ${x.value.totalScore} 分（${x.value.grade}）`]
}

// 登记结果
const cVisible = ref(false)
const c = reactive({ attendedUserIds: [] })
function openComplete() {
  const signed = d.value.participants.filter((p) => p.signTime).map((p) => String(p.userId))
  Object.assign(c, { actualTime: x.value.planTime, attendedUserIds: signed, participantCount: Math.max(1, signed.length), summary: '', problems: '' })
  cVisible.value = true
}
async function saveComplete() {
  if (!c.actualTime || !c.summary.trim()) return ElMessage.warning('请填写实际时间和演练总结')
  saving.value = true
  try {
    await completeEmergencyDrill(x.value.id, { ...c })
    cVisible.value = false
    changed('已登记，预案的上次演练时间已更新。下一步：评估打分')
  } finally {
    saving.value = false
  }
}

// 评估
const eVisible = ref(false)
const e = reactive({ scores: [], problems: [] })
function openEvaluate() {
  const tabletop = x.value.drillType === 'TABLETOP'
  Object.assign(e, {
    scores: d.value.scores.map((s) => ({ ...s, na: tabletop && ['MEDICAL', 'RECOVERY'].includes(s.code), score: s.max, remark: '' })),
    planSuitable: true,
    reviseNote: '',
    problems: (x.value.problems || '').split(/[；;。\n]/).map((t) => t.trim()).filter(Boolean).map((t) => ({ category: 'OTHER', description: t.slice(0, 300) }))
  })
  eVisible.value = true
}
const preview = computed(() => {
  const got = e.scores.filter((s) => !s.na)
  const max = got.reduce((t, s) => t + s.max, 0)
  const sum = got.reduce((t, s) => t + Number(s.score || 0), 0)
  const total = max ? Math.round((sum * 1000) / max) / 10 : 0
  const grade = total >= 90 ? '优秀' : total >= 75 ? '良好' : total >= 60 ? '合格' : '不合格'
  return { total, grade }
})
async function saveEvaluate() {
  if (!e.planSuitable && !e.reviseNote.trim()) return ElMessage.warning('预案需要修订时请写修订意见')
  if (e.problems.some((p) => !p.description.trim())) return ElMessage.warning('有问题没写描述')
  saving.value = true
  try {
    const r = await evaluateDrill(x.value.id, {
      scores: e.scores.map((s) => ({ code: s.code, score: s.na ? null : s.score, remark: s.remark })),
      planSuitable: e.planSuitable,
      reviseNote: e.reviseNote,
      problems: e.problems
    })
    eVisible.value = false
    changed(`评估完成：${r.total} 分（${r.grade}）`)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.head b { font-size: 16px; }
h4 { margin: 18px 0 8px; }
.muted { color: #999; font-size: 12px; font-weight: normal; }
.ml { margin-left: 8px; }
.mt { margin-top: 10px; }
.mb { margin-bottom: 10px; }
.ok { color: #52c41a; }
.mine { margin-top: 12px; padding: 8px 12px; background: #f0f9eb; border-radius: 6px; display: flex; align-items: center; gap: 8px; }
.actions { margin: 12px 0; display: flex; gap: 8px; flex-wrap: wrap; }
.pre { white-space: pre-wrap; word-break: break-all; }
.box { padding: 10px 12px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; font-size: 14px; line-height: 1.7; }
.box.warn { margin-top: 8px; background: #fdf6ec; border-color: #faecd8; }
.problem { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f2f2f2; }
.problem .desc { flex: 1; }
.link { color: #409eff; }
.total { margin: 10px 0 14px; }
.total b { font-size: 20px; color: #409eff; }
.ps { width: 100%; }
.p-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
</style>
