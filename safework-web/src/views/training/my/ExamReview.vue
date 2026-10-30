<template>
  <div class="review">
    <div class="summary" :class="result.passed ? 'pass' : 'fail'">
      <div class="score"><b>{{ result.score }}</b><span>/ {{ result.totalScore }}</span></div>
      <div class="info">
        <el-tag :type="result.passed ? 'success' : 'danger'" effect="dark" size="large">{{ result.passed ? '通过' : '未通过' }}</el-tag>
        <div class="line">及格分 {{ result.passScore }} · 答对 {{ result.correctCount }} / {{ result.items.length }} 题 · 用时 {{ duration(result.costSeconds) }}</div>
        <div class="line muted">
          第 {{ result.attemptNo }} 次 · 交卷时间 {{ result.submitTime }}
          <el-tag v-if="result.autoSubmit" size="small" type="warning">时间到自动交卷</el-tag>
        </div>
      </div>
    </div>
    <el-alert v-if="!result.showAnswer" type="info" :closable="false" show-icon class="no-answer"
              title="这场考试设置为不公布正确答案，只显示每题对错。" />
    <div class="filter">
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="all">全部 {{ result.items.length }}</el-radio-button>
        <el-radio-button value="wrong">答错 {{ result.items.length - result.correctCount }}</el-radio-button>
      </el-radio-group>
    </div>
    <div v-for="q in shown" :key="q.index" class="q" :class="q.correct ? 'right' : 'wrong'">
      <div class="q-head">
        <el-icon v-if="q.correct" class="mark ok"><CircleCheckFilled /></el-icon>
        <el-icon v-else class="mark bad"><CircleCloseFilled /></el-icon>
        <span class="no">{{ q.index }}.</span>
        <el-tag size="small" effect="plain" :type="QUESTION_TYPES[q.type]?.type">{{ QUESTION_TYPES[q.type]?.label }}</el-tag>
        <span class="content">{{ q.content }}</span>
        <span class="pts">{{ q.correct ? q.score : 0 }}/{{ q.score }} 分</span>
      </div>
      <div class="opts">
        <div v-for="(o, i) in q.options" :key="i" class="opt"
             :class="{ chosen: (q.userAnswer || '').includes(letter(i)), answer: q.answer && q.answer.includes(letter(i)) }">
          <span class="l">{{ letter(i) }}</span>{{ o }}
        </div>
      </div>
      <div class="foot">
        你的答案：<b>{{ show(q, q.userAnswer) || '未作答' }}</b>
        <template v-if="q.answer"> · 正确答案：<b class="ok">{{ show(q, q.answer) }}</b></template>
      </div>
      <div v-if="q.analysis" class="analysis">解析：{{ q.analysis }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { QUESTION_TYPES, letter } from '@/utils/dict'

const props = defineProps({ result: { type: Object, required: true } })
const filter = ref('all')
const shown = computed(() => (filter.value === 'wrong' ? props.result.items.filter((q) => !q.correct) : props.result.items))
function show(q, ans) {
  if (!ans) return ''
  return q.type === 'JUDGE' ? (ans === 'A' ? '正确' : '错误') : ans
}
function duration(s) {
  const m = Math.floor(s / 60)
  return m ? `${m} 分 ${s % 60} 秒` : `${s} 秒`
}
</script>

<style scoped>
.summary { display: flex; align-items: center; gap: 24px; padding: 18px 20px; border-radius: 10px; margin-bottom: 14px; }
.summary.pass { background: #f0f9eb; }
.summary.fail { background: #fef0f0; }
.score b { font-size: 46px; line-height: 1; }
.pass .score b { color: #67c23a; }
.fail .score b { color: #f56c6c; }
.score span { color: #909399; margin-left: 4px; }
.line { margin-top: 6px; color: #606266; font-size: 13px; }
.muted { color: #909399; }
.no-answer { margin-bottom: 10px; }
.filter { margin-bottom: 8px; }
.q { border: 1px solid #ebeef5; border-left-width: 4px; border-radius: 8px; padding: 10px 14px; margin-bottom: 10px; }
.q.right { border-left-color: #67c23a; }
.q.wrong { border-left-color: #f56c6c; }
.q-head { display: flex; align-items: flex-start; gap: 6px; line-height: 1.6; }
.mark { font-size: 18px; margin-top: 3px; }
.ok { color: #67c23a; }
.bad { color: #f56c6c; }
.no { font-weight: 600; }
.content { flex: 1; }
.pts { color: #909399; font-size: 12px; white-space: nowrap; }
.opts { margin: 6px 0 6px 26px; }
.opt { padding: 3px 8px; border-radius: 4px; font-size: 14px; }
.opt .l { display: inline-block; width: 20px; font-weight: 600; color: #909399; }
.opt.chosen { background: #fdf6ec; }
.opt.answer { background: #f0f9eb; color: #529b2e; font-weight: 600; }
.foot { margin-left: 26px; font-size: 13px; color: #606266; }
.analysis { margin: 6px 0 0 26px; font-size: 13px; color: #606266; background: #f6f8fb; border-radius: 4px; padding: 6px 10px; }
</style>
