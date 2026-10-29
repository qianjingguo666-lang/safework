<template>
  <div class="chat-page">
    <div class="page-card chat-main">
      <el-alert v-if="status && !status.chatEnabled" type="info" :closable="false" show-icon class="tip"
                title="当前未配置大模型：会返回知识库中最相关的原文。在 config/secret.yml 填写讯飞星火 APIPassword 后即可生成回答。" />
      <el-alert v-if="status && status.documentCount === 0" type="warning" :closable="false" show-icon class="tip"
                title="知识库还没有文档，请先在【AI 助手 → 知识库】上传安全制度文件。" />

      <div ref="listRef" class="messages">
        <div v-if="!messages.length" class="welcome">
          <el-icon :size="40" color="#1f6feb"><MagicStick /></el-icon>
          <h2>安全生产问答助手</h2>
          <p>回答依据企业上传的安全制度文件，并标注出处</p>
          <div class="samples">
            <el-tag v-for="q in SAMPLES" :key="q" effect="plain" class="sample" @click="ask(q)">{{ q }}</el-tag>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <div v-if="m.role === 'user'">{{ m.content }}</div>
            <template v-else>
              <!-- 先转义再加格式，避免把模型输出当 HTML 执行 -->
              <div class="answer" v-html="render(m.content)" @click="onCite($event, m)" />
              <span v-if="m.streaming" class="cursor">▍</span>
              <div v-if="m.error" class="err">{{ m.error }}</div>
              <div v-if="m.sources?.length" class="sources">
                <div class="src-title">参考来源</div>
                <div v-for="s in m.sources" :key="s.index" class="src" :class="{ active: m.active === s.index }"
                     @click="m.active = m.active === s.index ? null : s.index">
                  <div class="src-head">
                    <b>[{{ s.index }}]</b> 《{{ s.documentTitle }}》<span v-if="s.pageNo">第 {{ s.pageNo }} 页</span>
                    <span class="score">相关度 {{ s.score }}</span>
                  </div>
                  <div class="src-body" :class="{ open: m.active === s.index }">{{ s.content }}</div>
                </div>
              </div>
              <div v-if="m.costMs" class="meta">用时 {{ (m.costMs / 1000).toFixed(1) }} 秒</div>
            </template>
          </div>
        </div>
      </div>

      <div class="input">
        <el-input v-model="question" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" maxlength="500" resize="none"
                  placeholder="输入问题，Enter 发送，Shift + Enter 换行" @keydown.enter="onEnter" />
        <div class="actions">
          <el-button v-if="streaming" @click="stop">停止</el-button>
          <el-button v-else :disabled="!messages.length" @click="messages = []">清空</el-button>
          <el-button type="primary" :loading="streaming" :disabled="!question.trim()" @click="ask()">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { aiStatus } from '@/api'
import { postStream } from '@/utils/sse'

const SAMPLES = ['进入受限空间前要做哪些准备？', '氧含量多少才合格？', '发生中毒窒息事故怎么救援？', '动火作业要配备哪些消防器材？']
const status = ref(null)
const messages = ref([])
const question = ref('')
const streaming = ref(false)
const listRef = ref()
let controller = null

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function render(text) {
  return escapeHtml(text || '')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/\[(\d{1,2})\]/g, '<sup class="cite" data-i="$1">[$1]</sup>')
    .replace(/\n/g, '<br>')
}
function onCite(e, m) {
  const i = e.target?.dataset?.i
  if (i) m.active = Number(i)
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
  if (!text || streaming.value) return
  question.value = ''
  // 只把已完成的问答作为上下文传给后端
  const history = messages.value.filter((m) => !m.error).map((m) => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: text })
  const answer = reactive({ role: 'assistant', content: '', sources: [], streaming: true, active: null, error: '', costMs: 0 })
  messages.value.push(answer)
  scrollDown()

  streaming.value = true
  controller = new AbortController()
  try {
    await postStream('/ai/chat/stream', { question: text, history: history.slice(-6) }, {
      sources: (list) => { answer.sources = list },
      delta: (d) => { answer.content += d.t; scrollDown() },
      done: (d) => { answer.costMs = d.costMs },
      error: (d) => { answer.error = d.msg }
    }, controller.signal)
  } catch (e) {
    if (e.name !== 'AbortError') answer.error = e.message
  } finally {
    answer.streaming = false
    streaming.value = false
    scrollDown()
  }
}
function stop() {
  controller?.abort()
}

onMounted(async () => { status.value = await aiStatus() })
onBeforeUnmount(stop)
</script>

<style scoped>
.chat-page { height: calc(100vh - 56px - 32px); }
.chat-main { height: 100%; display: flex; flex-direction: column; box-sizing: border-box; padding: 16px; }
.tip { margin-bottom: 10px; }
.messages { flex: 1; overflow-y: auto; padding: 8px 4px; }
.welcome { text-align: center; padding: 48px 16px; color: #555; }
.welcome h2 { margin: 12px 0 6px; }
.welcome p { color: #888; margin: 0 0 20px; }
.samples { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.sample { cursor: pointer; }
.msg { display: flex; margin: 14px 0; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 78%; padding: 10px 14px; border-radius: 10px; line-height: 1.75; word-break: break-word; }
.msg.user .bubble { background: #1f6feb; color: #fff; border-bottom-right-radius: 2px; }
.msg.assistant .bubble { background: #f4f6f9; color: #1f2329; border-bottom-left-radius: 2px; }
.answer { display: inline; }
.answer :deep(.cite) { color: #1f6feb; cursor: pointer; font-weight: 600; margin: 0 1px; }
.cursor { animation: blink 1s infinite; color: #1f6feb; }
@keyframes blink { 50% { opacity: 0; } }
.err { color: #f56c6c; margin-top: 6px; }
.sources { margin-top: 10px; border-top: 1px dashed #dcdfe6; padding-top: 8px; }
.src-title { font-size: 12px; color: #888; margin-bottom: 4px; }
.src { font-size: 13px; padding: 6px 8px; border-radius: 6px; cursor: pointer; }
.src:hover, .src.active { background: #e8f0fe; }
.src-head { color: #444; }
.score { float: right; color: #aaa; font-size: 12px; }
.src-body { color: #666; margin-top: 4px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.src-body.open { display: block; }
.meta { font-size: 12px; color: #aaa; margin-top: 6px; }
.input { border-top: 1px solid #eee; padding-top: 12px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
@media (max-width: 768px) { .bubble { max-width: 92%; } }
</style>
