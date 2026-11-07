<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-button icon="ArrowLeft" @click="emit('back')">返回</el-button>
      <b class="title">{{ d?.assessment.title }}</b>
      <el-tag v-if="d" :type="draft ? 'warning' : 'success'">{{ draft ? '自评中' : '已完成' }}</el-tag>
      <div class="spacer" />
      <template v-if="d?.canEdit">
        <el-button icon="Refresh" :loading="refreshing" @click="refresh">重新取数</el-button>
        <el-button type="danger" plain icon="Delete" @click="remove">删除</el-button>
        <el-button type="primary" icon="Check" @click="openFinish">完成自评</el-button>
      </template>
      <el-button icon="Download" @click="exportExcel">导出扣分汇总</el-button>
    </div>

    <template v-if="d">
      <div class="head">
        <div class="score">
          <div class="num">{{ fmt(d.score) }}</div>
          <div class="muted">{{ draft ? '当前得分（按已填扣分实时计算）' : '自评得分' }}</div>
        </div>
        <div class="grade">
          <el-tag :type="GRADE_TYPES[d.grade]" effect="dark" size="large">{{ d.grade }}</el-tag>
          <div class="muted">近一年死亡 {{ d.dead }} 人、重伤 {{ d.serious }} 人</div>
        </div>
        <div class="info muted">
          自评组长：{{ d.assessment.leaderName }}<span v-if="d.assessment.members">；成员：{{ d.assessment.members }}</span><br />
          时间：{{ d.assessment.startDate }} ~ {{ d.assessment.finishDate || '进行中' }}；扣分项 {{ d.deductCount }} 项<span v-if="draft && d.noteMissing" class="danger">，{{ d.noteMissing }} 项还没写扣分说明</span><br />
          等级：一级 ≥ 90 分且近一年无重伤及以上事故；二级 ≥ 75 分且无死亡事故；三级 ≥ 60 分且死亡不超过 1 人（参考常见行业定级标准，以所在地区标准为准）
          <div v-if="d.assessment.summary" class="summary">自评结论：{{ d.assessment.summary }}</div>
        </div>
        <EChart class="radar" :option="radarOption" height="230px" />
      </div>

      <div class="filter">
        <el-checkbox v-model="onlyDeduct">只看扣分项</el-checkbox>
        <span class="muted">带“自动取证”标记的项目，系统已从各模块取数并给出建议扣分；其他项目需要评审人根据现场和文件检查打分</span>
      </div>

      <el-collapse v-model="opened">
        <el-collapse-item v-for="e in d.elements" :key="e.no" :name="e.no">
          <template #title>
            <span class="e-title">{{ e.no }}. {{ e.name }}</span>
            <span class="e-sub muted">满分 {{ e.full }}<template v-if="e.na">（空项 {{ e.na }}）</template>，扣 {{ fmt(e.deduct) }}，得分率 {{ e.rate ?? '-' }}%</span>
            <span v-if="prevRate(e.no) != null" class="e-prev muted">上次 {{ prevRate(e.no) }}%</span>
          </template>
          <div v-for="r in itemsOf(e.no)" :key="r.item.id" class="item" :class="{ deducted: !r.item.na && r.item.deduct > 0, na: r.item.na }">
            <div class="i-head">
              <b>{{ r.item.code }} {{ r.item.title }}</b>
              <span class="muted">标准分 {{ r.item.standardScore }}</span>
              <el-tag v-if="r.item.auto" size="small" type="info">自动取证</el-tag>
              <div class="spacer" />
              <template v-if="draft && d.canEdit">
                <el-checkbox v-model="r.edit.na" @change="save(r)">空项</el-checkbox>
                <span>扣</span>
                <el-input-number v-model="r.edit.deduct" :min="0" :max="r.item.standardScore" :step="0.5" :precision="1" size="small" style="width: 110px"
                                 :disabled="r.edit.na" @change="save(r)" />
                <span>分</span>
              </template>
              <template v-else>
                <el-tag v-if="r.item.na" size="small">空项</el-tag>
                <b v-else :class="r.item.deduct > 0 ? 'danger' : 'ok'">{{ r.item.deduct > 0 ? `扣 ${fmt(r.item.deduct)} 分` : '不扣分' }}</b>
              </template>
            </div>
            <div class="content">{{ r.item.content }}</div>
            <div class="method muted">考评办法：{{ r.item.method }}</div>
            <div v-if="r.evidence.length" class="evidence">
              <div v-for="(f, k) in r.evidence" :key="k">· {{ f }}</div>
              <div v-if="r.item.suggested != null" class="suggest">
                系统建议扣 <b>{{ fmt(r.item.suggested) }}</b> 分
                <el-button v-if="draft && d.canEdit && !r.edit.na && Number(r.edit.deduct) !== Number(r.item.suggested)" link type="primary" size="small"
                           @click="r.edit.deduct = r.item.suggested; save(r)">采用建议</el-button>
              </div>
            </div>
            <div v-if="draft && d.canEdit && !r.edit.na" class="note">
              <el-input v-model="r.edit.note" size="small" maxlength="300" :placeholder="r.edit.deduct > 0 ? '扣分说明（必填）：发现了什么问题' : '说明（可选）'"
                        @change="save(r)" />
            </div>
            <div v-else-if="r.item.note" class="note-text">扣分说明：{{ r.item.note }}</div>
            <div v-if="!r.item.na && r.item.deduct > 0 && !draft" class="hz">
              <template v-if="r.item.hazardNo">已转整改：<el-tag size="small" type="warning">{{ r.item.hazardNo }}</el-tag></template>
              <el-button v-else-if="canEditPerm" size="small" type="warning" plain @click="toHazard(r)">转隐患整改</el-button>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </template>

    <el-dialog v-model="fVisible" title="完成自评" width="520px">
      <p>当前得分 <b>{{ fmt(d?.score) }}</b>，等级 <b>{{ d?.grade }}</b>，扣分项 {{ d?.deductCount }} 项。完成后不能再修改。</p>
      <el-input v-model="summary" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="自评结论（可选）：体系运行情况、主要问题、整改安排" />
      <template #footer>
        <el-button @click="fVisible = false">取消</el-button>
        <el-button type="primary" :loading="finishing" @click="finish">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { deleteStd, finishStd, refreshStd, saveStdItem, stdDetail, stdToHazard } from '@/api'
import EChart from '@/components/EChart.vue'
import { downloadFile } from '@/utils/download'
import { GRADE_TYPES, fmt } from './std'

const props = defineProps({ id: { type: [String, Number], required: true } })
const emit = defineEmits(['back', 'changed'])
const store = useUserStore()
const canEditPerm = computed(() => store.hasPerm('std:edit'))
const d = ref(null)
const rows = ref([])
const loading = ref(false)
const refreshing = ref(false)
const opened = ref([1, 2, 3, 4, 5, 6, 7, 8])
const onlyDeduct = ref(false)
const fVisible = ref(false)
const finishing = ref(false)
const summary = ref('')
const draft = computed(() => d.value?.assessment.status === 'DRAFT')

const itemsOf = (no) => rows.value.filter((r) => r.item.element === no && (!onlyDeduct.value || (!r.item.na && r.item.deduct > 0)))
const prevRate = (no) => d.value?.previous?.find((e) => e.no === no)?.rate ?? null
const radarOption = computed(() => {
  const els = d.value.elements
  const series = [{ value: els.map((e) => e.rate ?? 0), name: '本次', areaStyle: { opacity: 0.2 }, itemStyle: { color: '#1677ff' } }]
  if (d.value.previous) series.push({ value: d.value.previous.map((e) => e.rate ?? 0), name: '上次', lineStyle: { type: 'dashed' }, itemStyle: { color: '#faad14' } })
  return {
    tooltip: {},
    legend: { data: series.map((s) => s.name), bottom: 0, textStyle: { fontSize: 11 } },
    radar: { radius: '62%', center: ['50%', '46%'], indicator: els.map((e) => ({ name: e.name.length > 6 ? e.name.slice(0, 6) + '…' : e.name, max: 100 })), axisName: { fontSize: 11, color: '#666' } },
    series: [{ type: 'radar', data: series }]
  }
})

async function load() {
  loading.value = true
  try {
    d.value = await stdDetail(props.id)
    rows.value = d.value.items.map((r) => ({ ...r, edit: { na: !!r.item.na, deduct: Number(r.item.deduct), note: r.item.note || '' } }))
  } finally {
    loading.value = false
  }
}
async function save(r) {
  try {
    await saveStdItem(r.item.id, { na: r.edit.na, deduct: r.edit.na ? 0 : r.edit.deduct || 0, note: r.edit.note })
    r.item.na = r.edit.na ? 1 : 0
    r.item.deduct = r.edit.na ? 0 : r.edit.deduct || 0
    r.item.note = r.edit.note
    const fresh = await stdDetail(props.id)
    Object.assign(d.value, { score: fresh.score, grade: fresh.grade, elements: fresh.elements, deductCount: fresh.deductCount, noteMissing: fresh.noteMissing })
  } catch (e) {
    load()
  }
}
async function refresh() {
  refreshing.value = true
  try {
    const n = await refreshStd(props.id)
    ElMessage.success(n ? `已重新取数，${n} 项的建议扣分有变化（已填的扣分没有改动）` : '已重新取数，建议扣分没有变化')
    load()
  } finally {
    refreshing.value = false
  }
}
function openFinish() {
  summary.value = ''
  fVisible.value = true
}
async function finish() {
  finishing.value = true
  try {
    await finishStd(props.id, summary.value)
    ElMessage.success('自评已完成')
    fVisible.value = false
    emit('changed')
    load()
  } finally {
    finishing.value = false
  }
}
async function remove() {
  await ElMessageBox.confirm('删除这次进行中的自评？已填的扣分会一起删除。', '删除自评', { type: 'warning' })
  await deleteStd(props.id)
  ElMessage.success('已删除')
  emit('back')
}
async function toHazard(r) {
  await ElMessageBox.confirm(`把“${r.item.code} ${r.item.title}”转成一条隐患，走隐患整改、验收闭环流程？`, '转隐患整改', { type: 'warning' })
  const res = await stdToHazard(r.item.id)
  r.item.hazardNo = res.hazardNo
  r.item.hazardId = res.hazardId
  ElMessage.success(`已生成隐患 ${res.hazardNo}，请到隐患排查里指派整改`)
}
function exportExcel() {
  downloadFile(`/safety/std/${props.id}/export`, {}, '标准化自评扣分汇总.xlsx')
}
onMounted(load)
</script>

<style scoped>
.title { font-size: 16px; margin: 0 6px; }
.muted { color: #999; font-size: 12px; }
.danger { color: #f5222d; }
.ok { color: #52c41a; }
.head { display: flex; gap: 24px; align-items: center; flex-wrap: wrap; border: 1px solid #ebeef5; border-radius: 10px; padding: 12px 18px; margin-bottom: 12px; }
.score { text-align: center; min-width: 110px; }
.num { font-size: 40px; font-weight: 700; color: #1677ff; line-height: 1.1; }
.grade { text-align: center; display: flex; flex-direction: column; gap: 6px; align-items: center; }
.info { flex: 1; min-width: 300px; line-height: 1.8; }
.summary { margin-top: 4px; color: #555; }
.radar { width: 320px; }
.filter { display: flex; gap: 16px; align-items: center; margin: 4px 0 8px; flex-wrap: wrap; }
.e-title { font-weight: 600; font-size: 14px; }
.e-sub { margin-left: 12px; }
.e-prev { margin-left: 10px; color: #d48806; }
.item { border: 1px solid #ebeef5; border-left: 4px solid #c0c4cc; border-radius: 6px; padding: 8px 12px; margin-bottom: 8px; }
.item.deducted { border-left-color: #f5222d; }
.item.na { opacity: 0.6; }
.i-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.content { margin: 4px 0 2px; }
.evidence { background: #f6f8fa; border-radius: 4px; padding: 6px 10px; margin-top: 6px; font-size: 13px; line-height: 1.7; color: #444; }
.suggest { color: #1677ff; }
.note { margin-top: 6px; }
.note-text { margin-top: 6px; color: #cf1322; font-size: 13px; }
.hz { margin-top: 6px; font-size: 13px; }
</style>
