<template>
  <div class="print-page">
    <div class="toolbar no-print">
      <el-button icon="Back" @click="goBack">返回</el-button>
      <el-button type="primary" icon="Printer" :disabled="!d" @click="doPrint">打印 / 另存为 PDF</el-button>
      <span class="tip">打印时选择 A4 纸、纵向；想要 PDF 文件，打印机选「另存为 PDF」</span>
    </div>

    <div v-if="loading" class="state no-print">加载中…</div>
    <div v-else-if="error" class="state no-print"><el-result icon="error" :title="error" /></div>

    <div v-else-if="d" class="sheet">
      <div v-if="watermark" class="watermark">{{ watermark }}</div>

      <header class="sheet-head">
        <div class="titles">
          <div class="tenant">{{ d.tenantName }}</div>
          <h1>{{ TICKET_TYPES[t.ticketType]?.label }}安全作业票</h1>
          <div class="meta">
            <span>编号：<b>{{ t.ticketNo }}</b></span>
            <span>状态：<b>{{ TICKET_STATUS[t.status]?.label }}</b></span>
          </div>
        </div>
        <div class="qr">
          <img v-if="qr" :src="qr" alt="验真二维码">
          <div>扫码验真</div>
          <div class="code">{{ shortCode }}</div>
        </div>
      </header>

      <table class="grid">
        <colgroup><col style="width: 17%"><col style="width: 33%"><col style="width: 17%"><col style="width: 33%"></colgroup>
        <tr><th>作业名称</th><td colspan="3">{{ t.title }}</td></tr>
        <tr><th>作业地点</th><td>{{ t.location }}</td><th>受限空间</th><td>{{ t.spaceName || '—' }}</td></tr>
        <tr><th>作业负责人</th><td>{{ t.applicantName }}</td><th>监护人</th><td>{{ t.guardianName }}</td></tr>
        <tr v-if="t.contractorName"><th>承包商</th><td>{{ t.contractorName }}</td><th>作业人员</th><td>{{ t.contractorWorkerNames }}</td></tr>
        <tr><th>计划时间</th><td colspan="3">{{ hm(t.planStartTime) }} 至 {{ hm(t.planEndTime) }}</td></tr>
        <tr><th>实际时间</th><td colspan="3">{{ t.actualStartTime ? hm(t.actualStartTime) : '　　　　' }} 至 {{ t.actualEndTime ? hm(t.actualEndTime) : '　　　　' }}</td></tr>
        <tr><th>作业内容</th><td colspan="3" class="pre">{{ t.workContent || '—' }}</td></tr>
        <tr><th>安全措施</th><td colspan="3" class="pre tall">{{ t.safetyMeasures || '—' }}</td></tr>
      </table>

      <template v-if="TICKET_TYPES[t.ticketType]?.gas">
        <h2>气体检测</h2>
        <table class="grid center">
          <tr><th>检测项目</th><th>合格标准</th><th>检测结果</th><th>是否合格</th></tr>
          <tr v-for="g in gasRows" :key="g.name">
            <td>{{ g.name }}</td><td>{{ g.standard }}</td><td>{{ g.value }}</td><td :class="{ bad: g.ok === false }">{{ g.ok == null ? '—' : g.ok ? '合格' : '不合格' }}</td>
          </tr>
          <tr><th>检测时间</th><td colspan="3">{{ t.gasCheckTime ? hm(t.gasCheckTime) : '—' }}</td></tr>
        </table>
      </template>

      <h2>审批与确认</h2>
      <table class="grid sign-table">
        <colgroup><col style="width: 16%"><col style="width: 17%"><col><col style="width: 19%"><col style="width: 21%"></colgroup>
        <tr><th>环节</th><th>人员</th><th>意见</th><th>时间</th><th>签名</th></tr>
        <tr v-for="l in steps" :key="l.id">
          <td>{{ STEP_NAME[l.action] || TICKET_ACTIONS[l.action] }}</td>
          <td>{{ l.operatorName }}</td>
          <td class="comment">{{ l.comment || (l.action === 'APPROVE' ? '同意' : '') }}</td>
          <td class="time">{{ hm(l.createTime) }}</td>
          <td class="sign"><img v-if="l.signImage" :src="l.signImage" alt="签名"><span v-else class="unsigned">未签名</span></td>
        </tr>
        <tr v-for="p in pendingSteps" :key="p">
          <td>{{ p }}</td><td /><td /><td /><td class="sign" />
        </tr>
      </table>

      <footer class="sheet-foot">
        <div>1. 本票是作业的唯一许可凭证，作业时随身携带、现场张贴；作业内容、地点、人员变化须重新办票。</div>
        <div>2. 扫描右上角二维码查看系统里的实时状态；票面与系统不一致时，以系统为准，立即停止作业。</div>
        <div class="printed">打印人：{{ d.printedBy }}　打印时间：{{ d.printTime }}　验真码：{{ shortCode }}　SafeWork 智能安全作业管理平台</div>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { printTicket } from '@/api'
import { TICKET_ACTIONS, TICKET_STATUS, TICKET_TYPES } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
const d = ref(null)
const qr = ref('')
const t = computed(() => d.value.ticket)

const STEP_NAME = { SUBMIT: '申请人提交', APPROVE: '审批', REJECT: '驳回', START: '监护人确认开工', COMPLETE: '监护人确认完工', CANCEL: '取消' }
const SHOWN = ['SUBMIT', 'APPROVE', 'REJECT', 'START', 'COMPLETE', 'CANCEL']

// 只显示最后一次提交以后的记录：被驳回前的审批已经不算数
const steps = computed(() => {
  const logs = d.value.logs.filter((l) => SHOWN.includes(l.action))
  const lastSubmit = logs.map((l) => l.action).lastIndexOf('SUBMIT')
  return lastSubmit >= 0 ? logs.slice(lastSubmit) : logs
})
// 还没走到的环节留空白行，打印出来可以看出缺了哪一步
const pendingSteps = computed(() => {
  const s = t.value.status
  if (['CANCELLED', 'REJECTED', 'COMPLETED'].includes(s)) return []
  const rows = []
  const a = d.value.approval
  if (s === 'PENDING' && a && a.currentNode != null) {
    for (let i = a.currentNode; i < a.totalNodes; i++) rows.push(`审批：${a.nodes[i].name}`)
  }
  if (s === 'PENDING' || s === 'APPROVED') rows.push('监护人确认开工')
  if (s !== 'DRAFT') rows.push('监护人确认完工')
  return rows
})

const gasRows = computed(() => {
  const v = t.value
  const row = (name, standard, value, unit, ok) => ({ name, standard, value: value == null ? '—' : `${value} ${unit}`, ok: value == null ? null : ok(Number(value)) })
  return [
    row('氧含量', '19.5% ~ 23.5%', v.gasOxygen, '%VOL', (x) => x >= 19.5 && x <= 23.5),
    row('可燃气体', '≤ 10% LEL', v.gasLel, '%LEL', (x) => x <= 10),
    row('硫化氢', '≤ 10 mg/m³', v.gasH2s, 'mg/m³', (x) => x <= 10)
  ]
})

// 不能作为作业凭证的票加水印
const watermark = computed(() => {
  const v = d.value.verdict
  if (!v || v.level === 'OK') return ''
  return { DRAFT: '草稿 不可作业', PENDING: '审批中 不可作业', REJECTED: '已驳回 不可作业', CANCELLED: '已作废', COMPLETED: '已完工', APPROVED: '已过期', IN_PROGRESS: '已超时' }[t.value.status] || '不可作业'
})
const shortCode = computed(() => (d.value?.verifyToken || '').slice(0, 8).toUpperCase())
const hm = (s) => (s ? String(s).slice(0, 16) : '')

onMounted(async () => {
  try {
    d.value = await printTicket(route.params.id)
    document.title = `${d.value.ticket.ticketNo} ${d.value.ticket.title}`
    qr.value = await QRCode.toDataURL(`${window.location.origin}/verify/ticket/${d.value.verifyToken}`, { width: 240, margin: 1 })
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
})

const doPrint = () => window.print()
function goBack() {
  if (window.history.length > 1 && window.opener == null) router.back()
  else window.close()
}
</script>

<style scoped>
.print-page { min-height: 100vh; background: #e9ecf1; padding: 16px 0 32px; }
.toolbar { max-width: 210mm; margin: 0 auto 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 0 8px; }
.tip { color: #888; font-size: 12px; }
.state { text-align: center; padding-top: 20vh; color: #888; }
.sheet { position: relative; width: 210mm; min-height: 297mm; margin: 0 auto; padding: 12mm 12mm 10mm; background: #fff; box-shadow: 0 2px 12px rgba(0, 0, 0, .12); box-sizing: border-box; color: #111; font-size: 12.5px; overflow: hidden; }
.watermark { position: absolute; left: 50%; top: 45%; transform: translate(-50%, -50%) rotate(-28deg); font-size: 64px; font-weight: 700; color: rgba(220, 38, 38, .13); white-space: nowrap; pointer-events: none; letter-spacing: 8px; }
.sheet-head { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #111; padding-bottom: 6px; margin-bottom: 10px; }
.tenant { font-size: 13px; color: #333; }
h1 { margin: 4px 0 6px; font-size: 22px; letter-spacing: 2px; }
.meta { display: flex; gap: 18px; font-size: 12.5px; }
.qr { text-align: center; font-size: 11px; color: #333; }
.qr img { width: 26mm; height: 26mm; display: block; }
.qr .code { font-family: Consolas, monospace; letter-spacing: 1px; }
h2 { font-size: 14px; margin: 12px 0 6px; }
.grid { width: 100%; border-collapse: collapse; table-layout: fixed; }
.grid th, .grid td { border: 1px solid #333; padding: 5px 7px; vertical-align: middle; word-break: break-all; }
.grid th { background: #f2f3f5; font-weight: 600; text-align: center; }
.grid.center td { text-align: center; }
.pre { white-space: pre-wrap; line-height: 1.6; }
.tall { height: 22mm; vertical-align: top; }
.bad { color: #c62828; font-weight: 700; }
.sign-table td { height: 15mm; }
.sign-table .comment { font-size: 12px; line-height: 1.5; }
.sign-table .time { white-space: nowrap; font-size: 11.5px; }
.sign { text-align: center; padding: 2px !important; }
.sign img { max-width: 100%; max-height: 14mm; }
.unsigned { color: #999; font-size: 11px; }
.sheet-foot { margin-top: 12px; font-size: 11px; color: #333; line-height: 1.8; }
.printed { margin-top: 6px; padding-top: 6px; border-top: 1px dashed #999; color: #666; }

@media screen and (max-width: 820px) {
  .sheet { width: auto; min-height: 0; margin: 0 8px; padding: 14px 12px; }
  .watermark { font-size: 40px; }
}

@page { size: A4 portrait; margin: 0; }
@media print {
  .no-print { display: none !important; }
  .print-page { background: #fff; padding: 0; min-height: 0; }
  .sheet { box-shadow: none; margin: 0; width: 210mm; min-height: 297mm; }
  .grid th { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  .watermark { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
}
</style>
