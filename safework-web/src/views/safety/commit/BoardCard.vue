<template>
  <div class="board">
    <div class="title">安全承诺公告</div>
    <div class="sub">{{ tenantName }} · {{ c.commitDate }}</div>
    <div class="units" v-if="c.unitsTotal">
      生产装置 <b>{{ c.unitsTotal }}</b> 套，其中运行 <b>{{ c.unitsRunning }}</b> 套、停产 <b>{{ c.unitsStopped }}</b> 套、检修 <b>{{ c.unitsOverhaul }}</b> 套；
      {{ c.trialRun ? '处于试生产' : '不处于试生产' }}；{{ c.startupShutdown ? '有开停车' : '没有开停车' }}{{ c.conditionNote ? `（${c.conditionNote}）` : '' }}
    </div>
    <div class="stats">
      <div v-for="[k, label] in STAT_LABELS" :key="k" class="stat"><b :class="{ warn: warnKeys.includes(k) && board.stats[k] }">{{ board.stats[k] }}</b><span>{{ label }}</span></div>
    </div>
    <div class="content">{{ c.content }}</div>
    <div v-if="abnormal.length" class="abn">
      <div class="abn-title">研判发现的异常和管控措施</div>
      <div v-for="(x, i) in abnormal" :key="i" class="abn-row">{{ i + 1 }}. 【{{ x.label }}】{{ x.text }}：{{ x.note }}。措施：{{ x.measure }}</div>
    </div>
    <div class="judgers">研判人：{{ board.aspects.map((a) => `${a.label} ${a.judgerName || '-'}`).join('，') }}</div>
    <div class="sign">
      <div>主要负责人：</div>
      <img v-if="board.signImage" :src="board.signImage" alt="签名" />
      <div class="muted">{{ c.committerName }} · {{ c.commitTime?.slice(0, 16) }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { STAT_LABELS } from './commit'

const props = defineProps({ board: { type: Object, required: true } })
const store = useUserStore()
const tenantName = computed(() => store.tenantName || '')
const c = computed(() => props.board.commitment)
const warnKeys = ['overstock', 'openMajorHazards']
const abnormal = computed(() => props.board.aspects.flatMap((a) => a.items.filter((i) => i.ok === false).map((i) => ({ ...i, label: a.label }))))
</script>

<style scoped>
.board { max-width: 860px; margin: 0 auto; padding: 24px 28px; border: 2px solid #c0392b; border-radius: 8px; background: #fffdf8; }
.title { text-align: center; font-size: 26px; font-weight: 700; color: #c0392b; letter-spacing: 6px; }
.sub { text-align: center; color: #666; margin: 6px 0 16px; }
.units { line-height: 1.8; margin-bottom: 12px; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-bottom: 14px; }
.stat { border: 1px solid #f0e2d0; border-radius: 6px; padding: 8px; text-align: center; background: #fff; }
.stat b { display: block; font-size: 22px; }
.stat b.warn { color: #f56c6c; }
.stat span { color: #888; font-size: 12px; }
.content { font-size: 16px; line-height: 2; text-indent: 2em; margin: 8px 0 12px; }
.abn { background: #fef6f6; border: 1px solid #f5c2c2; border-radius: 6px; padding: 8px 12px; margin-bottom: 12px; }
.abn-title { font-weight: 600; color: #c0392b; margin-bottom: 4px; }
.abn-row { line-height: 1.7; font-size: 14px; }
.judgers { color: #555; font-size: 13px; margin-bottom: 10px; }
.sign { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
.sign img { height: 60px; }
.muted { color: #999; font-size: 12px; }
@media (max-width: 600px) {
  .board { padding: 14px; }
  .title { font-size: 20px; letter-spacing: 2px; }
  .stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
