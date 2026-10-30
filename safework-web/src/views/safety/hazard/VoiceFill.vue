<template>
  <div class="voice-fill">
    <div class="vf-head">
      <el-icon class="vf-icon"><MagicStick /></el-icon>
      <b>一句话上报</b>
      <span class="muted">说清楚在哪里、发现了什么问题，AI 帮你填表</span>
    </div>
    <el-input v-model="text" type="textarea" :rows="2" maxlength="500" resize="none"
              placeholder="例如：二号原料储罐旁边的可燃气体报警器不亮了，按测试按钮也不响" />
    <div class="vf-actions">
      <el-button v-if="recordable" :type="recording ? 'danger' : 'default'" :icon="recording ? 'VideoPause' : 'Microphone'"
                 :loading="transcribing" :disabled="parsing" @click="toggle">
        {{ recording ? `说完了（${seconds}s）` : transcribing ? '识别中…' : '语音输入' }}
      </el-button>
      <span v-if="recording" class="meter"><i :style="{ width: Math.round(level * 100) + '%' }" /></span>
      <el-button type="primary" plain icon="MagicStick" :loading="parsing" :disabled="!text.trim() || recording || transcribing" @click="parse">
        AI 填写
      </el-button>
      <span v-if="!recordable && hint" class="muted small">{{ hint }}</span>
    </div>
    <div v-if="result" class="vf-result">
      <div class="muted small">
        已填写下方表单（紫色底色，{{ result.aiUsed ? '大模型提取' : '关键词提取' }}，{{ (result.costMs / 1000).toFixed(1) }} 秒），<b>请核对后再提交</b>
      </div>
      <div v-for="(n, i) in result.notes" :key="i" class="note"><el-icon><WarningFilled /></el-icon>{{ n }}</div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiStatus, parseHazardText, transcribeHazard } from '@/api'
import { MAX_SECONDS, canRecord, startRecording } from '@/utils/recorder'

const emit = defineEmits(['fill'])
const text = ref('')
const result = ref(null)
const recordable = ref(false)
const hint = ref('')
const recording = ref(false)
const transcribing = ref(false)
const parsing = ref(false)
const seconds = ref(0)
const level = ref(0)
let recorder = null
let timer = null

onMounted(async () => {
  let asr = false
  try {
    asr = (await aiStatus()).asrEnabled
  } catch {
    asr = false
  }
  if (!asr) {
    hint.value = '没有配置讯飞语音听写，可以直接打字，或者用手机输入法的语音输入'
  } else if (!canRecord()) {
    hint.value = '浏览器只允许在 https 网站使用麦克风，可以用手机输入法的语音输入'
  }
  recordable.value = asr && canRecord()
})

async function toggle() {
  if (recording.value) return finish()
  try {
    recorder = await startRecording({ onLevel: (v) => (level.value = v), onAutoStop: () => finish() })
  } catch (e) {
    ElMessage.error(e?.name === 'NotAllowedError' ? '没有麦克风权限：请在浏览器地址栏左侧允许使用麦克风' : '打开麦克风失败：' + (e?.message || e))
    return
  }
  recording.value = true
  seconds.value = 0
  const begin = Date.now()
  timer = setInterval(() => {
    seconds.value = Math.floor((Date.now() - begin) / 1000)
    if (seconds.value >= MAX_SECONDS) finish()
  }, 250)
}

async function finish() {
  if (!recording.value) return
  recording.value = false
  clearInterval(timer)
  const pcm = await recorder.stop()
  recorder = null
  transcribing.value = true
  try {
    const r = await transcribeHazard(pcm)
    text.value = text.value.trim() ? `${text.value.trim()} ${r.text}` : r.text
  } finally {
    transcribing.value = false
  }
  // 识别完直接提取，少点一次按钮
  await parse()
}

async function parse() {
  if (!text.value.trim()) return
  parsing.value = true
  try {
    result.value = await parseHazardText(text.value.trim())
    emit('fill', result.value)
  } finally {
    parsing.value = false
  }
}

function reset() {
  cancel()
  text.value = ''
  result.value = null
}
function cancel() {
  clearInterval(timer)
  if (recorder) recorder.cancel()
  recorder = null
  recording.value = false
}
onBeforeUnmount(cancel)
defineExpose({ reset, cancel })
</script>

<style scoped>
.voice-fill { margin-bottom: 16px; padding: 12px; border-radius: 8px; background: linear-gradient(135deg, #f4f2ff, #eef5ff); border: 1px solid #e1dcff; }
.vf-head { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.vf-icon { color: #7c5cff; }
.muted { color: #888; font-size: 12px; }
.small { font-size: 12px; }
.vf-actions { display: flex; align-items: center; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.vf-actions .el-button + .el-button { margin-left: 0; }
.meter { width: 80px; height: 6px; border-radius: 3px; background: #e4e7ed; overflow: hidden; }
.meter i { display: block; height: 100%; background: #f56c6c; transition: width .1s; }
.vf-result { margin-top: 8px; }
.note { display: flex; align-items: center; gap: 4px; margin-top: 4px; color: #e6a23c; font-size: 12px; }
</style>
