<template>
  <el-dialog :model-value="modelValue" :title="`${ASPECTS[aspect] || ''}研判`" width="min(680px, 96vw)" append-to-body
             @update:model-value="emit('update:modelValue', $event)" @open="init">
    <template v-if="aspect === 'PRODUCTION'">
      <div class="sec">生产装置运行情况</div>
      <div class="units">
        <span>共</span><el-input-number v-model="f.unitsTotal" :min="1" :max="999" size="small" controls-position="right" /><span>套，运行</span>
        <el-input-number v-model="f.unitsRunning" :min="0" :max="999" size="small" controls-position="right" /><span>套，停产</span>
        <el-input-number v-model="f.unitsStopped" :min="0" :max="999" size="small" controls-position="right" /><span>套，检修</span>
        <el-input-number v-model="f.unitsOverhaul" :min="0" :max="999" size="small" controls-position="right" /><span>套</span>
      </div>
      <div v-if="unitsWrong" class="err">运行 + 停产 + 检修要等于总数</div>
      <div class="flags">
        <el-checkbox v-model="f.trialRun">处于试生产</el-checkbox>
        <el-checkbox v-model="f.startupShutdown">今天有开停车</el-checkbox>
        <el-input v-if="f.trialRun || f.startupShutdown" v-model="f.conditionNote" size="small" maxlength="200" placeholder="说明（哪套装置、什么时间）" />
      </div>
    </template>
    <div class="sec">逐项研判</div>
    <div v-for="r in f.results" :key="r.code" class="item" :class="{ bad: !r.ok }">
      <div class="row">
        <span class="text">{{ r.text }}</span>
        <el-radio-group v-model="r.ok" size="small">
          <el-radio-button :value="true">正常</el-radio-button>
          <el-radio-button :value="false">异常</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="!r.ok" class="abn">
        <el-input v-model="r.note" size="small" maxlength="200" placeholder="异常情况" />
        <el-input v-model="r.measure" size="small" maxlength="200" placeholder="管控措施" />
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交研判</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { commitJudge } from '@/api'
import { ASPECTS } from './commit'

const props = defineProps({
  modelValue: Boolean,
  aspect: { type: String, default: '' },
  /** today 接口返回的这个方面（含 items） */
  view: { type: Object, default: null },
  commitment: { type: Object, default: null },
  lastUnits: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])
const saving = ref(false)
const f = reactive({ results: [] })
const unitsWrong = computed(() => props.aspect === 'PRODUCTION'
  && (f.unitsRunning || 0) + (f.unitsStopped || 0) + (f.unitsOverhaul || 0) !== (f.unitsTotal || 0))

function init() {
  const c = props.commitment
  const u = c?.unitsTotal ? { total: c.unitsTotal, running: c.unitsRunning, stopped: c.unitsStopped, overhaul: c.unitsOverhaul } : props.lastUnits
  Object.assign(f, {
    results: (props.view?.items || []).map((i) => ({ code: i.code, text: i.text, ok: i.ok !== false, note: i.note || '', measure: i.measure || '' })),
    unitsTotal: u?.total ?? 1, unitsRunning: u?.running ?? 1, unitsStopped: u?.stopped ?? 0, unitsOverhaul: u?.overhaul ?? 0,
    trialRun: !!c?.trialRun, startupShutdown: !!c?.startupShutdown, conditionNote: c?.conditionNote || ''
  })
}
async function submit() {
  if (unitsWrong.value) return ElMessage.warning('装置数对不上')
  const bad = f.results.find((r) => !r.ok && (!r.note.trim() || !r.measure.trim()))
  if (bad) return ElMessage.warning(`“${bad.text}”有异常，要写情况和管控措施`)
  saving.value = true
  try {
    await commitJudge({ aspect: props.aspect, ...f, results: f.results.map(({ code, ok, note, measure }) => ({ code, ok, note, measure })) })
    ElMessage.success('研判已提交')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sec { font-weight: 600; margin: 4px 0 8px; }
.units { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.units .el-input-number { width: 90px; }
.err { color: #f56c6c; font-size: 12px; margin-top: 4px; }
.flags { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin: 10px 0 14px; }
.flags .el-input { flex: 1; min-width: 200px; }
.item { padding: 8px 10px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; }
.item.bad { border-color: #f5c2c2; background: #fef6f6; }
.row { display: flex; align-items: center; gap: 10px; justify-content: space-between; }
.text { flex: 1; line-height: 1.5; }
.abn { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.abn .el-input { flex: 1; min-width: 200px; }
</style>
