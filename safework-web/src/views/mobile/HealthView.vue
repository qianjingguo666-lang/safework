<template>
  <div v-loading="loading">
    <template v-if="d">
      <div v-if="r" class="m-card">
        <div class="m-item-head">
          <span class="m-item-title">下次在岗体检</span>
          <el-tag v-if="r.contraindication" type="danger" effect="dark">职业禁忌证</el-tag>
          <el-tag :type="OH_STATES[r.state]?.type" effect="dark">{{ OH_STATES[r.state]?.label }}</el-tag>
        </div>
        <div class="next" :class="r.state">
          {{ r.nextExamDate || '还没有做过体检' }}
          <span v-if="r.daysLeft != null">{{ r.daysLeft < 0 ? `已逾期 ${-r.daysLeft} 天` : `还有 ${r.daysLeft} 天` }}</span>
        </div>
        <div class="m-row"><span>岗位</span><span>{{ r.posts }}</span></div>
        <div class="m-row"><span>接触危害因素</span><span>{{ r.factors }}</span></div>
        <div class="m-row"><span>体检周期</span><span>每 {{ r.cycleMonths }} 个月</span></div>
        <div v-if="r.state !== 'OK'" class="m-muted tip">请联系职业卫生管理员安排体检</div>
      </div>
      <div v-else class="m-card m-muted">你所在的岗位没有接触职业病危害因素，不需要职业健康体检。</div>

      <div class="m-card">
        <h3>我的体检记录</h3>
        <div v-if="!d.exams.length" class="m-muted">还没有体检记录</div>
        <div v-for="e in d.exams" :key="e.id" class="exam">
          <div class="m-item-head">
            <b>{{ e.examDate }}　{{ OH_EXAM_TYPES[e.examType] }}</b>
            <el-tag size="small" :type="OH_CONCLUSIONS[e.conclusion]?.type">{{ OH_CONCLUSIONS[e.conclusion]?.label }}</el-tag>
          </div>
          <div v-if="e.org" class="m-muted">{{ e.org }}</div>
          <div v-if="e.detail" class="detail">{{ e.detail }}</div>
          <div v-if="e.recheckDate && e.handleStatus === 'OPEN'" class="recheck">请在 {{ e.recheckDate }} 前复查</div>
          <div v-if="e.handleStatus === 'CLOSED'" class="handled">已处理：{{ e.handleMeasure }}</div>
        </div>
      </div>
      <div class="m-muted privacy">体检结果属于个人健康信息，只有你本人和职业卫生管理员能看到</div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { healthMine } from '@/api'
import { OH_CONCLUSIONS, OH_EXAM_TYPES, OH_STATES } from '@/utils/dict'

const loading = ref(false)
const d = ref(null)
const r = computed(() => d.value?.ledger)
onMounted(async () => {
  loading.value = true
  try {
    d.value = await healthMine()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.next { font-size: 22px; font-weight: 600; margin: 6px 0 10px; color: #52c41a; }
.next span { font-size: 14px; font-weight: normal; margin-left: 6px; }
.next.OVERDUE, .next.NEVER { color: #f5222d; }
.next.DUE_SOON { color: #fa8c16; }
.tip { margin-top: 6px; }
.exam { padding: 10px 0; border-bottom: 1px solid #f2f3f5; }
.exam:last-child { border-bottom: 0; }
.detail { font-size: 14px; color: #555; margin-top: 4px; }
.recheck { font-size: 14px; color: #fa8c16; margin-top: 4px; }
.handled { font-size: 13px; color: #52c41a; margin-top: 4px; }
.privacy { text-align: center; padding: 4px 0 12px; font-size: 12px; }
</style>
