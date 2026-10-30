// 浏览器录音 → 16k 采样、16bit、单声道 PCM（讯飞语音听写要求的格式）
// 注意：浏览器只允许在 https 或 localhost 下使用麦克风

export const MAX_SECONDS = 60

/** 当前环境能不能录音 */
export function canRecord() {
  return !!(window.isSecureContext && navigator.mediaDevices?.getUserMedia && (window.AudioContext || window.webkitAudioContext))
}

/**
 * 开始录音。onLevel(0~1) 实时音量，onAutoStop 到 60 秒自动停止时回调。
 * 返回 { stop(): Promise<Blob>, cancel() }
 */
export async function startRecording({ onLevel, onAutoStop } = {}) {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } })
  const Ctx = window.AudioContext || window.webkitAudioContext
  const ctx = new Ctx()
  const source = ctx.createMediaStreamSource(stream)
  // ScriptProcessor 已不推荐，但兼容性最好（包括手机浏览器），录几十秒的语音足够
  const processor = ctx.createScriptProcessor(4096, 1, 1)
  const chunks = []
  let length = 0
  let stopped = false
  processor.onaudioprocess = (e) => {
    if (stopped) return
    const data = e.inputBuffer.getChannelData(0)
    chunks.push(new Float32Array(data))
    length += data.length
    if (onLevel) {
      let peak = 0
      for (let i = 0; i < data.length; i += 16) peak = Math.max(peak, Math.abs(data[i]))
      onLevel(Math.min(1, peak * 1.5))
    }
    if (length / ctx.sampleRate >= MAX_SECONDS && onAutoStop) onAutoStop()
  }
  source.connect(processor)
  processor.connect(ctx.destination)

  const release = () => {
    stopped = true
    processor.disconnect()
    source.disconnect()
    stream.getTracks().forEach((t) => t.stop())
    ctx.close()
  }
  return {
    async stop() {
      const rate = ctx.sampleRate
      release()
      const merged = new Float32Array(length)
      let offset = 0
      for (const c of chunks) {
        merged.set(c, offset)
        offset += c.length
      }
      return new Blob([toPcm16k(merged, rate)], { type: 'application/octet-stream' })
    },
    cancel: release
  }
}

/** 降采样到 16k（区间取平均，顺带滤掉一部分高频），再转成 16bit 整数 */
export function toPcm16k(samples, rate) {
  const ratio = rate / 16000
  const out = new Int16Array(Math.floor(samples.length / ratio))
  for (let i = 0; i < out.length; i++) {
    const from = Math.floor(i * ratio)
    const to = Math.min(samples.length, Math.max(from + 1, Math.floor((i + 1) * ratio)))
    let sum = 0
    for (let j = from; j < to; j++) sum += samples[j]
    const v = Math.max(-1, Math.min(1, sum / (to - from)))
    out[i] = v < 0 ? v * 0x8000 : v * 0x7fff
  }
  return new Uint8Array(out.buffer)
}
