// 手机端工具：判断设备、记住“用电脑版”、把消息里的电脑端链接换成手机端页面、压缩照片

const KEY = 'sw-prefer-desktop'

/** 手机、平板（或者很窄的窗口） */
export function isMobileDevice() {
  return /Android|iPhone|iPad|iPod|Mobile|HarmonyOS/i.test(navigator.userAgent) || window.innerWidth < 768
}

/** 用户在手机上点过“切换到电脑版”，以后就不再自动跳到手机版 */
export function preferDesktop() {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}
export function setPreferDesktop(v) {
  try {
    if (v) localStorage.setItem(KEY, '1')
    else localStorage.removeItem(KEY)
  } catch {
    /* 隐私模式下存不了，不影响使用 */
  }
}

/** 消息里存的是电脑端链接，手机端有对应页面的换成手机端页面 */
export function mobileLink(link) {
  if (!link) return null
  if (link.startsWith('/safety/ppe')) return '/m/ppe'
  if (link.startsWith('/safety/health')) return '/m/health'
  if (link.startsWith('/safety/resp')) return '/m/resp'
  const m = link.match(/^\/(ticket\/list|safety\/hazard|safety\/check)\?detail=(\d+)/)
  if (!m) return null
  return { 'ticket/list': `/m/ticket/${m[2]}`, 'safety/hazard': `/m/hazard/${m[2]}`, 'safety/check': `/m/check/${m[2]}` }[m[1]]
}

/**
 * 手机拍的照片动辄 3~8 MB，上传前缩到长边 1600 像素、JPEG 质量 0.8（一般 300~600 KB）。
 * 压缩失败（比如浏览器不支持这种格式）就原样上传。
 */
export async function compressImage(file, maxSide = 1600, quality = 0.8) {
  if (!file.type.startsWith('image/') || file.size < 500 * 1024) return file
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close?.()
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (!blob || blob.size >= file.size) return file
    return new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' })
  } catch {
    return file
  }
}

/** 列表里显示的相对时间：今天 14:30 / 昨天 09:10 / 10-28 16:00 */
export function shortTime(s) {
  if (!s) return ''
  const d = new Date(String(s).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return s
  const p = (n) => String(n).padStart(2, '0')
  const hm = `${p(d.getHours())}:${p(d.getMinutes())}`
  const today = new Date()
  const diff = Math.round((new Date(today.getFullYear(), today.getMonth(), today.getDate()) - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 86400000)
  if (diff === 0) return `今天 ${hm}`
  if (diff === 1) return `昨天 ${hm}`
  if (diff === -1) return `明天 ${hm}`
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${hm}`
}
