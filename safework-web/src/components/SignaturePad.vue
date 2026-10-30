<template>
  <div class="sign-pad">
    <canvas ref="canvasRef" :class="{ empty }" @pointerdown="down" @pointermove="move" @pointerup="up" @pointerleave="up" @pointercancel="up" />
    <span v-if="empty" class="placeholder">{{ placeholder }}</span>
    <el-button class="clear" link size="small" icon="RefreshLeft" :disabled="empty" @click="clear">重签</el-button>
  </div>
</template>

<script setup>
// 手写签名板：鼠标、手指、触控笔都可以（Pointer Events）。导出时裁掉空白、缩放到 600×200 以内的 PNG
import { nextTick, onMounted, ref } from 'vue'

defineProps({ placeholder: { type: String, default: '请在此处手写签名' } })
const canvasRef = ref()
const empty = ref(true)
let ctx = null
let drawing = false
let last = null
// 笔迹的外框，导出时只保留这一块
let box = null

function setup() {
  const c = canvasRef.value
  const ratio = window.devicePixelRatio || 1
  const rect = c.getBoundingClientRect()
  c.width = Math.max(1, Math.round(rect.width * ratio))
  c.height = Math.max(1, Math.round(rect.height * ratio))
  ctx = c.getContext('2d')
  ctx.scale(ratio, ratio)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.strokeStyle = '#111'
  ctx.lineWidth = 2.6
}
onMounted(() => nextTick(setup))

function point(e) {
  const rect = canvasRef.value.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}
function grow(p) {
  box = box ? { x1: Math.min(box.x1, p.x), y1: Math.min(box.y1, p.y), x2: Math.max(box.x2, p.x), y2: Math.max(box.y2, p.y) } : { x1: p.x, y1: p.y, x2: p.x, y2: p.y }
}
function down(e) {
  if (!ctx) setup()
  e.preventDefault()
  canvasRef.value.setPointerCapture?.(e.pointerId)
  drawing = true
  last = point(e)
  grow(last)
  ctx.beginPath()
  ctx.arc(last.x, last.y, ctx.lineWidth / 2, 0, Math.PI * 2)
  ctx.fillStyle = '#111'
  ctx.fill()
}
function move(e) {
  if (!drawing) return
  e.preventDefault()
  const p = point(e)
  ctx.beginPath()
  ctx.moveTo(last.x, last.y)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
  last = p
  grow(p)
  empty.value = false
}
function up() {
  drawing = false
}

function clear() {
  const c = canvasRef.value
  ctx?.clearRect(0, 0, c.width, c.height)
  box = null
  empty.value = true
}

/** 导出 PNG data URL；没签返回 null */
function toDataURL() {
  if (empty.value || !box) return null
  const c = canvasRef.value
  const ratio = c.width / c.getBoundingClientRect().width
  const pad = 8
  const sx = Math.max(0, (box.x1 - pad) * ratio)
  const sy = Math.max(0, (box.y1 - pad) * ratio)
  const sw = Math.min(c.width - sx, (box.x2 - box.x1 + pad * 2) * ratio)
  const sh = Math.min(c.height - sy, (box.y2 - box.y1 + pad * 2) * ratio)
  // 太小的签名放大到至少 160×60，避免后端认为尺寸不对；太大的缩小到 600×200 以内
  const scale = Math.min(600 / sw, 200 / sh, Math.max(1, 160 / sw, 60 / sh))
  const out = document.createElement('canvas')
  out.width = Math.max(60, Math.round(sw * scale))
  out.height = Math.max(24, Math.round(sh * scale))
  out.getContext('2d').drawImage(c, sx, sy, sw, sh, 0, 0, out.width, out.height)
  return out.toDataURL('image/png')
}

defineExpose({ clear, toDataURL, isEmpty: () => empty.value })
</script>

<style scoped>
.sign-pad { position: relative; width: 100%; height: 160px; border: 1px dashed #c0c4cc; border-radius: 6px; background: #fcfcfd; touch-action: none; }
canvas { width: 100%; height: 100%; display: block; cursor: crosshair; touch-action: none; }
.placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #c0c4cc; pointer-events: none; font-size: 14px; }
.clear { position: absolute; right: 8px; top: 6px; }
</style>
