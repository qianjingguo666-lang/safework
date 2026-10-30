<template>
  <div class="agent-page">
    <div class="page-card agent-main">
      <el-alert v-if="status && !status.chatEnabled" type="warning" :closable="false" show-icon class="tip"
                title="智能体需要大模型：在 config/secret.yml 配置支持 Function Calling 的模型（例如 DeepSeek）后重启后端。" />

      <div ref="listRef" class="messages">
        <div v-if="!messages.length" class="welcome">
          <el-icon :size="40" color="#1f6feb"><Cpu /></el-icon>
          <h2>安全智能体</h2>
          <p>用一句话提问，AI 会自己决定查询哪些数据，再根据真实数据回答</p>
          <div class="samples">
            <el-tag v-for="q in SAMPLES" :key="q" effect="plain" class="sample" @click="ask(q)">{{ q }}</el-tag>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <div v-if="m.role === 'user'">{{ m.content }}</div>
            <template v-else>
              <!-- 调用过程：每次工具调用一行，实时更新 -->
              <div v-if="m.steps.length || m.thinking" class="steps">
                <div v-for="s in m.steps" :key="s.id" class="step">
                  <el-icon v-if="s.status === 'running'" class="is-loading running"><Loading /></el-icon>
                  <el-icon v-else-if="s.ok" class="ok"><CircleCheckFilled /></el-icon>
                  <el-icon v-else class="fail"><CircleCloseFilled /></el-icon>
                  <span class="step-label">{{ s.label }}</span>
                  <span v-if="argsText(s.args)" class="step-args">{{ argsText(s.args) }}</span>
                  <span v-if="s.summary" class="step-summary" :class="{ fail: !s.ok }">{{ s.summary }}</span>
                  <span v-if="s.costMs != null" class="step-cost">{{ s.costMs }} ms</span>
                </div>
                <div v-if="m.thinking" class="step thinking">
                  <el-icon class="is-loading running"><Loading /></el-icon>
                  <span>{{ m.round > 1 ? '分析查询结果…' : '理解问题，选择要查询的数据…' }}</span>
                </div>
              </div>
              <!-- 先转义再加格式，避免把模型输出当 HTML 执行 -->
              <div v-if="m.content" class="answer" v-html="render(m.content)" />
              <!-- 写操作的确认卡片：用户点击确认后才真正执行 -->
              <div v-for="c in m.cards" :key="c.actionId" class="action-card" :class="c.state">
                <div class="ac-head">
                  <el-icon><EditPen /></el-icon>
                  <span class="ac-title">{{ c.title }}</span>
                  <el-tag size="small" :type="CARD_STATES[c.state].type" effect="dark">{{ CARD_STATES[c.state].label }}</el-tag>
                </div>
                <div v-for="f in c.fields" :key="f.label" class="ac-field">
                  <span class="ac-label">{{ f.label }}</span>
                  <span class="ac-value" :class="{ long: f.value && f.value.length > 40 }">{{ f.value }}</span>
                </div>
                <div v-if="c.state === 'pending' || c.state === 'confirming'" class="ac-foot">
                  <span class="ac-tip">{{ c.tip }}<template v-if="c.expiresAt">（{{ c.expiresAt }} 前有效）</template></span>
                  <el-button size="small" :disabled="c.state === 'confirming'" @click="cancelCard(c)">取消</el-button>
                  <el-button size="small" type="primary" :loading="c.state === 'confirming'" @click="confirmCard(c)">确认执行</el-button>
                </div>
                <div v-else-if="c.result" class="ac-result">
                  <el-icon v-if="c.state === 'done'"><CircleCheckFilled /></el-icon>
                  <el-icon v-else-if="c.state === 'error'"><CircleCloseFilled /></el-icon>
                  {{ c.result }}
                </div>
              </div>
              <div v-if="m.error" class="err">{{ m.error }}</div>
              <div v-if="m.done" class="meta">
                {{ m.done.rounds }} 轮推理 · 调用 {{ m.steps.length }} 次工具 · {{ m.done.tokens }} tokens · 用时 {{ (m.done.costMs / 1000).toFixed(1) }} 秒
                <span v-if="m.done.limited">（已达到最大轮数）</span>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="input">
        <el-input v-model="question" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="500" resize="none"
                  placeholder="例如：现在有哪些逾期没整改的隐患？Enter 发送，Shift + Enter 换行" @keydown.enter="onEnter" />
        <div class="actions">
          <el-button v-if="running" @click="stop">停止</el-button>
          <el-button v-else :disabled="!messages.length" @click="messages = []">清空</el-button>
          <el-button type="primary" :loading="running" :disabled="!question.trim()" @click="ask()">发送</el-button>
        </div>
      </div>
    </div>

    <div class="page-card side">
      <div class="side-title">我可以帮你</div>
      <div v-for="t in tools" :key="t.name" class="tool">
        <div class="tool-name">
          <el-icon><EditPen v-if="t.action" /><Search v-else /></el-icon>{{ t.label }}
          <el-tag v-if="t.action" size="small" type="warning" effect="plain">需确认</el-tag>
        </div>
        <div class="tool-desc">{{ t.description }}</div>
      </div>
      <el-empty v-if="!tools.length" description="当前账号没有可用的查询工具" :image-size="60" />
      <div class="side-note">
        <el-icon><Lock /></el-icon>
        <span>查到的范围和你在页面上能看到的一致；修改类操作会先生成确认卡片，由你核对并点击确认后才执行。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { agentTools, aiStatus, cancelAgentAction, confirmAgentAction } from '@/api'
import { postStream } from '@/utils/sse'

const SAMPLES = [
  '现在有哪些整改中的隐患？哪些已经逾期？',
  '最近 30 天安全情况怎么样？',
  '高风险的受限空间有哪些？进入前要做哪些气体检测？',
  '催办所有已经逾期的隐患',
  '把待指派的隐患指派给李四整改，3 天内完成'
]
const CARD_STATES = {
  pending: { label: '待确认', type: 'warning' },
  confirming: { label: '执行中', type: 'primary' },
  done: { label: '已执行', type: 'success' },
  cancelled: { label: '已取消', type: 'info' },
  error: { label: '未执行', type: 'danger' }
}
// 参数名 → 中文，调用过程里显示得更直观
const VALUE_NAMES = {
  REPORTED: '待指派', RECTIFYING: '整改中', PENDING_VERIFY: '待验收', CLOSED: '已闭环', REVOKED: '已撤销',
  GENERAL: '一般', MAJOR: '重大', HIGH: '高', MEDIUM: '中', LOW: '低',
  DRAFT: '草稿', PENDING: '待审批', APPROVED: '已批准', IN_PROGRESS: '作业中', COMPLETED: '已完成', REJECTED: '已驳回', CANCELLED: '已取消',
  CONFINED_SPACE: '受限空间', HOT_WORK: '动火', HEIGHT: '高处', true: '是', false: '否'
}
const ARG_NAMES = {
  status: '状态', level: '等级', overdue: '逾期', keyword: '关键词', limit: '条数', ticketType: '类型',
  riskLevel: '风险', spaceType: '空间类型', days: '天数', query: '检索'
}
const status = ref(null)
const tools = ref([])
const messages = ref([])
const question = ref('')
const running = ref(false)
const listRef = ref()
let controller = null

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
// 简单的 Markdown：标题、加粗、行内代码、表格、分隔线（模型回答常用的格式）
function inline(s) {
  return escapeHtml(s)
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}
function cells(line) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim())
}
function render(text) {
  const lines = (text || '').split(/\r?\n/)
  const out = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    // 连续以 | 开头的行是一张表格；第二行 |---|---| 是表头分隔
    if (line.trim().startsWith('|')) {
      const rows = []
      while (i < lines.length && lines[i].trim().startsWith('|')) rows.push(lines[i++])
      i--
      const body = rows.filter((r) => !/^\s*\|?[\s:|-]+\|?\s*$/.test(r))
      const hasHead = rows.length > 1 && /^\s*\|?[\s:|-]+\|?\s*$/.test(rows[1])
      let html = '<div class="md-table"><table>'
      body.forEach((r, k) => {
        const tag = hasHead && k === 0 ? 'th' : 'td'
        html += '<tr>' + cells(r).map((c) => `<${tag}>${inline(c)}</${tag}>`).join('') + '</tr>'
      })
      out.push(html + '</table></div>')
      continue
    }
    if (!line.trim()) { out.push(''); continue }
    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) { out.push('<hr>'); continue }
    const h = line.match(/^#{1,4}\s*(.+)$/)
    out.push((h ? `<b>${inline(h[1])}</b>` : inline(line)) + '<br>')
  }
  // 空行：连续的只留一个，表格、分隔线前后的去掉（它们自带间距）
  const block = (x) => x === undefined || x.startsWith('<div class="md-table"') || x === '<hr>'
  return out.filter((x, k) => x !== '' || !(out[k - 1] === '' || block(out[k - 1]) || block(out[k + 1])))
    .map((x) => (x === '' ? '<div class="gap"></div>' : x)).join('')
}
function argsText(args) {
  if (!args || typeof args !== 'object') return ''
  return Object.entries(args)
    .filter(([, v]) => v !== null && v !== '')
    .map(([k, v]) => `${ARG_NAMES[k] || k}：${VALUE_NAMES[String(v).toUpperCase()] || VALUE_NAMES[String(v)] || v}`)
    .join('，')
}
function scrollDown() {
  nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight })
}
function onEnter(e) {
  if (!e.shiftKey) {
    e.preventDefault()
    ask()
  }
}

async function ask(q) {
  const text = (q || question.value).trim()
  if (!text || running.value) return
  question.value = ''
  // 只把成功的问答作为上下文；确认卡片的处理结果也带上，模型才知道哪些操作已经执行了
  const history = messages.value.filter((m) => !m.error && m.content).map((m) => ({
    role: m.role,
    content: m.content + (m.cards || []).map((c) => `\n[${c.title}：${CARD_STATES[c.state].label}${c.result ? '，' + c.result : ''}]`).join('')
  }))
  messages.value.push({ role: 'user', content: text })
  const answer = reactive({ role: 'assistant', content: '', steps: [], cards: [], thinking: true, round: 0, error: '', done: null })
  messages.value.push(answer)
  scrollDown()

  running.value = true
  controller = new AbortController()
  try {
    await postStream('/ai/agent/chat', { question: text, history: history.slice(-6) }, {
      thinking: (d) => { answer.thinking = true; answer.round = d.round; scrollDown() },
      tool_start: (d) => {
        answer.thinking = false
        answer.steps.push({ ...d, status: 'running', ok: false, summary: '', costMs: null })
        scrollDown()
      },
      tool_end: (d) => {
        const s = answer.steps.find((x) => x.id === d.id)
        if (s) Object.assign(s, { status: 'done', ok: d.ok, summary: d.summary, costMs: d.costMs })
      },
      confirm: (card) => { answer.cards.push({ ...card, state: 'pending', result: '' }) },
      answer: (d) => { answer.thinking = false; answer.content = d.t; scrollDown() },
      done: (d) => { answer.done = d },
      error: (d) => { answer.error = d.msg }
    }, controller.signal)
  } catch (e) {
    if (e.name !== 'AbortError') answer.error = e.message
  } finally {
    answer.thinking = false
    answer.steps.forEach((s) => { if (s.status === 'running') Object.assign(s, { status: 'done', ok: false, summary: '已中断' }) })
    running.value = false
    scrollDown()
  }
}
function stop() {
  controller?.abort()
}
async function confirmCard(c) {
  c.state = 'confirming'
  try {
    const r = await confirmAgentAction(c.actionId)
    Object.assign(c, { state: 'done', result: r.message })
  } catch (e) {
    Object.assign(c, { state: 'error', result: e.message })
  }
}
async function cancelCard(c) {
  try {
    await cancelAgentAction(c.actionId)
    Object.assign(c, { state: 'cancelled', result: '已取消，没有做任何修改' })
  } catch (e) {
    Object.assign(c, { state: 'error', result: e.message })
  }
}

onMounted(async () => {
  const [st, list] = await Promise.all([aiStatus(), agentTools()])
  status.value = st
  tools.value = list || []
})
onBeforeUnmount(stop)
</script>

<style scoped>
.agent-page { flex: 1 !important; min-height: 420px; display: flex; gap: 16px; }
.agent-main { flex: 1; min-width: 0; display: flex; flex-direction: column; box-sizing: border-box; padding: 16px; }
.side { width: 280px; flex-shrink: 0; overflow-y: auto; box-sizing: border-box; padding: 16px; }
.tip { margin-bottom: 10px; }
.messages { flex: 1; overflow-y: auto; padding: 8px 4px; }
.welcome { text-align: center; padding: 48px 16px; color: #555; }
.welcome h2 { margin: 12px 0 6px; }
.welcome p { color: #888; margin: 0 0 20px; }
.samples { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.sample { cursor: pointer; }
.msg { display: flex; margin: 14px 0; }
.msg.user { justify-content: flex-end; }
.bubble { box-sizing: border-box; max-width: 82%; padding: 10px 14px; border-radius: 10px; line-height: 1.75; word-break: break-word; }
.msg.user .bubble { background: #1f6feb; color: #fff; border-bottom-right-radius: 2px; }
.msg.assistant .bubble { background: #f4f6f9; color: #1f2329; border-bottom-left-radius: 2px; min-width: 260px; }
.steps { background: #fff; border: 1px solid #e4e7ed; border-radius: 8px; padding: 6px 10px; margin-bottom: 8px; font-size: 13px; }
.step { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 3px 0; line-height: 1.5; }
.step + .step { border-top: 1px dashed #ebeef5; }
.step .ok { color: #67c23a; }
.step .fail, .step-summary.fail { color: #f56c6c; }
.running { color: #1f6feb; }
.step-label { font-weight: 600; color: #303133; }
.step-args { color: #909399; background: #f4f4f5; border-radius: 4px; padding: 0 6px; }
.step-summary { color: #606266; }
.step-cost { color: #c0c4cc; margin-left: auto; font-size: 12px; }
.thinking { color: #909399; }
.action-card { background: #fff; border: 1px solid #f3d19e; border-left: 4px solid #e6a23c; border-radius: 8px; padding: 10px 14px; margin-top: 10px; font-size: 13px; }
.action-card.done { border-color: #c2e7b0; border-left-color: #67c23a; }
.action-card.cancelled { border-color: #e4e7ed; border-left-color: #c0c4cc; opacity: 0.8; }
.action-card.error { border-color: #fbc4c4; border-left-color: #f56c6c; }
.ac-head { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 14px; margin-bottom: 6px; }
.ac-title { flex: 1; }
.ac-field { display: flex; gap: 10px; padding: 3px 0; line-height: 1.6; }
.ac-label { width: 76px; flex-shrink: 0; color: #909399; }
.ac-value { flex: 1; color: #303133; word-break: break-all; }
.ac-value.long { white-space: pre-wrap; background: #fafafa; border-radius: 4px; padding: 4px 8px; max-height: 180px; overflow-y: auto; }
.ac-foot { display: flex; align-items: center; gap: 8px; border-top: 1px dashed #ebeef5; margin-top: 8px; padding-top: 8px; }
.ac-tip { flex: 1; color: #909399; font-size: 12px; }
.ac-result { display: flex; align-items: center; gap: 4px; border-top: 1px dashed #ebeef5; margin-top: 8px; padding-top: 8px; color: #606266; }
.action-card.done .ac-result { color: #529b2e; }
.action-card.error .ac-result { color: #f56c6c; }
.answer :deep(.md-table) { overflow-x: auto; margin: 6px 0; }
.answer :deep(table) { border-collapse: collapse; font-size: 13px; line-height: 1.5; }
.answer :deep(th), .answer :deep(td) { border: 1px solid #dcdfe6; padding: 4px 8px; text-align: left; white-space: nowrap; }
.answer :deep(th) { background: #eef1f6; font-weight: 600; }
.answer :deep(tr:nth-child(even) td) { background: #fafbfc; }
.answer :deep(.gap) { height: 8px; }
.answer :deep(hr) { border: none; border-top: 1px solid #e4e7ed; margin: 8px 0; }
.answer :deep(code) { background: #e8eaed; border-radius: 3px; padding: 0 4px; }
.err { color: #f56c6c; margin-top: 6px; }
.meta { font-size: 12px; color: #aaa; margin-top: 6px; }
.input { border-top: 1px solid #eee; padding-top: 12px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.side-title { font-weight: 600; margin-bottom: 10px; }
.tool { padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.tool-name { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-weight: 600; color: #1f6feb; font-size: 14px; }
.tool-desc { color: #888; font-size: 12px; line-height: 1.6; margin-top: 4px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.side-note { display: flex; gap: 6px; align-items: flex-start; color: #909399; font-size: 12px; margin-top: 14px; line-height: 1.6; }
@media (max-width: 900px) {
  .side { display: none; }
  .bubble { max-width: 94%; }
  .msg.assistant .bubble { min-width: 0; }
}
</style>
