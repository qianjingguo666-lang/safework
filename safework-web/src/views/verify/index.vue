<template>
  <div class="verify">
    <div v-if="loading" class="center">正在核验…</div>
    <div v-else-if="error" class="center">
      <el-result icon="error" :title="error" sub-title="这张作业票不能作为作业凭证，请联系安全管理人员" />
    </div>
    <template v-else-if="v">
      <div class="banner" :class="v.verdict.level">
        <div class="tenant">{{ v.tenantName }}</div>
        <div class="big">{{ ICON[v.verdict.level] }} {{ v.verdict.level === 'OK' ? '真实有效' : v.verdict.level === 'WARN' ? '已关闭' : '不能作业' }}</div>
        <div class="text">{{ v.verdict.text }}</div>
        <div class="check">系统核验时间 {{ v.checkTime }}</div>
      </div>

      <div class="card">
        <div class="title">{{ v.typeLabel }} · {{ v.ticketNo }}</div>
        <div class="row"><span>作业名称</span><b>{{ v.title }}</b></div>
        <div class="row"><span>作业地点</span>{{ v.location }}</div>
        <div v-if="v.spaceName" class="row"><span>受限空间</span>{{ v.spaceName }}</div>
        <div class="row"><span>当前状态</span><el-tag :type="TICKET_STATUS[v.status]?.type" effect="dark">{{ v.statusLabel }}</el-tag></div>
        <div class="row"><span>作业负责人</span>{{ v.applicantName }}</div>
        <div class="row"><span>监护人</span>{{ v.guardianName }}</div>
        <div class="row"><span>计划时间</span><span class="val">{{ v.planStartTime }} ~ {{ v.planEndTime }}</span></div>
        <div v-if="v.actualStartTime" class="row"><span>实际开工</span>{{ v.actualStartTime }}</div>
        <div v-if="v.actualEndTime" class="row"><span>实际完工</span>{{ v.actualEndTime }}</div>
      </div>

      <div v-if="v.gasCheckTime" class="card gas">
        <div class="title">最近一次气体检测（{{ v.gasCheckTime }}）</div>
        O₂ {{ v.gasOxygen ?? '-' }}%VOL · 可燃 {{ v.gasLel ?? '-' }}%LEL · H₂S {{ v.gasH2s ?? '-' }}mg/m³
      </div>

      <h3>审批与确认记录</h3>
      <div class="card steps">
        <div v-for="(s, i) in v.steps" :key="i" class="step">
          <div class="dot" />
          <div class="s-body">
            <div><b>{{ s.action }}</b>　{{ s.operatorName }}<el-tag v-if="s.signed" size="small" type="success" effect="plain" class="signed">已手写签名</el-tag></div>
            <div class="muted">{{ s.time }}</div>
          </div>
        </div>
        <div v-if="!v.steps.length" class="muted">暂无记录</div>
      </div>

      <div class="card tip">核对纸质票上的编号、作业名称、人员是否与上面一致；不一致说明票面被涂改或使用了别的票，<b>立即停止作业</b>。</div>
      <div class="foot">SafeWork 智能安全作业管理平台</div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { verifyTicket } from '@/api'
import { TICKET_STATUS } from '@/utils/dict'

const ICON = { OK: '✔', WARN: '■', DANGER: '✖' }
const route = useRoute()
const loading = ref(true)
const error = ref('')
const v = ref(null)

onMounted(async () => {
  try {
    v.value = await verifyTicket(route.params.token)
  } catch (e) {
    error.value = e?.message || '二维码无效'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.verify { max-width: 520px; margin: 0 auto; padding-bottom: 24px; min-height: 100vh; background: #f3f5f8; }
.center { padding-top: 25vh; text-align: center; color: #888; }
.banner { color: #fff; padding: 22px 16px 18px; }
.banner.OK { background: linear-gradient(135deg, #1f9d55, #157a40); }
.banner.WARN { background: linear-gradient(135deg, #5b6575, #3a4352); }
.banner.DANGER { background: linear-gradient(135deg, #e6553a, #b8341c); }
.tenant { opacity: .85; font-size: 13px; }
.big { font-size: 26px; font-weight: 700; margin: 6px 0; }
.text { font-size: 14px; line-height: 1.6; }
.check { margin-top: 8px; opacity: .8; font-size: 12px; }
h3 { margin: 18px 16px 8px; font-size: 15px; }
.card { background: #fff; margin: 12px 16px 0; padding: 12px 14px; border-radius: 8px; line-height: 1.9; }
.title { font-weight: 600; margin-bottom: 4px; }
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.row > span:first-child { color: #888; flex: none; }
.val { text-align: right; }
.gas { background: #f0f9eb; color: #2e7d32; font-size: 13px; }
.steps { padding-top: 8px; }
.step { display: flex; gap: 10px; padding: 6px 0; border-bottom: 1px solid #f0f2f5; }
.step:last-child { border-bottom: 0; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; margin-top: 10px; flex: none; }
.signed { margin-left: 6px; }
.muted { color: #999; font-size: 12px; line-height: 1.6; }
.tip { background: #fff7e6; color: #8a5a00; font-size: 13px; line-height: 1.7; }
.foot { text-align: center; color: #aaa; font-size: 12px; margin-top: 24px; }
</style>
