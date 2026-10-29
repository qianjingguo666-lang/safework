<template>
  <div class="scan">
    <div v-if="loading" class="center">加载中…</div>
    <div v-else-if="error" class="center error">
      <el-result icon="error" :title="error" sub-title="请联系安全管理人员" />
    </div>
    <template v-else-if="s">
      <div class="banner" :class="bannerClass">
        <div class="tenant">{{ s.tenantName }}</div>
        <h1>{{ s.spaceName }}</h1>
        <div class="code">{{ s.spaceCode }} · {{ s.spaceType }} · {{ s.location }}</div>
        <div class="state">{{ stateText }}</div>
      </div>

      <div class="card">
        <div class="row"><span>风险等级</span><el-tag :type="RISK_LEVELS[s.riskLevel]?.type" effect="dark">{{ RISK_LEVELS[s.riskLevel]?.label }}</el-tag></div>
        <div class="row"><span>危险介质</span><b>{{ s.medium || '-' }}</b></div>
        <div class="row"><span>所属部门</span>{{ s.deptName || '-' }}</div>
        <div class="row"><span>责任人</span>{{ s.responsibleName || '-' }}</div>
      </div>

      <div class="card warn">
        <b>进入前必须：</b>持有效作业票 · 先通风再检测 · 监护人在场 · 佩戴防护用品
      </div>

      <h3>当前作业票</h3>
      <div v-if="!s.activeTickets.length" class="card empty">当前没有有效作业票，<b>禁止进入</b></div>
      <div v-for="t in s.activeTickets" :key="t.ticketNo" class="card ticket">
        <div class="t-head">
          <b>{{ t.ticketNo }}</b>
          <el-tag :type="TICKET_STATUS[t.status]?.type">{{ TICKET_STATUS[t.status]?.label }}</el-tag>
        </div>
        <div>{{ t.title }}</div>
        <div class="muted">负责人 {{ t.applicantName }} · 监护人 {{ t.guardianName }}</div>
        <div class="muted">计划 {{ t.planStartTime }} ~ {{ t.planEndTime }}</div>
        <div v-if="t.gasCheckTime" class="gas">
          O₂ {{ t.gasOxygen }}% · 可燃 {{ t.gasLel }}%LEL · H₂S {{ t.gasH2s ?? '-' }}mg/m³
          <div class="muted">检测于 {{ t.gasCheckTime }}</div>
        </div>
      </div>

      <template v-if="s.recentTickets.length">
        <h3>最近完成</h3>
        <div v-for="t in s.recentTickets" :key="t.ticketNo" class="card muted">
          {{ t.ticketNo }} {{ t.title }}　{{ t.actualEndTime }}
        </div>
      </template>
      <div class="foot">SafeWork 智能安全作业管理平台</div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { scanSpace } from '@/api'
import { RISK_LEVELS, TICKET_STATUS } from '@/utils/dict'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const s = ref(null)

const stateText = computed(() => {
  if (s.value.working) return '● 正在作业中'
  if (s.value.activeTickets.length) return '● 已批准，等待开工'
  return '● 当前无作业，禁止进入'
})
const bannerClass = computed(() => (s.value.working ? 'working' : s.value.activeTickets.length ? 'ready' : 'idle'))

onMounted(async () => {
  try {
    s.value = await scanSpace(route.params.token)
  } catch (e) {
    error.value = e.message || '二维码无效'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.scan { max-width: 520px; margin: 0 auto; padding: 0 0 24px; min-height: 100vh; background: #f3f5f8; }
.center { padding-top: 30vh; text-align: center; color: #888; }
.banner { color: #fff; padding: 24px 16px 20px; }
.banner.idle { background: linear-gradient(135deg, #5b6575, #3a4352); }
.banner.ready { background: linear-gradient(135deg, #1f6feb, #164fa8); }
.banner.working { background: linear-gradient(135deg, #e6553a, #b8341c); }
.tenant { opacity: .8; font-size: 13px; }
.banner h1 { margin: 6px 0; font-size: 24px; }
.code { opacity: .85; font-size: 13px; }
.state { margin-top: 12px; font-weight: 600; }
h3 { margin: 18px 16px 8px; font-size: 15px; }
.card { background: #fff; margin: 12px 16px 0; padding: 12px 14px; border-radius: 8px; line-height: 1.8; }
.row { display: flex; justify-content: space-between; align-items: center; }
.row span { color: #888; }
.warn { background: #fff7e6; color: #8a5a00; font-size: 13px; }
.empty { color: #c0392b; text-align: center; }
.t-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #888; font-size: 13px; }
.gas { margin-top: 6px; padding: 6px 10px; background: #f0f9eb; border-radius: 4px; color: #2e7d32; font-size: 13px; }
.foot { text-align: center; color: #aaa; font-size: 12px; margin-top: 24px; }
</style>
