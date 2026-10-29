import request from '@/api/request'

/** 从 Content-Disposition 里取文件名：attachment; filename*=UTF-8''%E5%8F%B0%E8%B4%A6.xlsx */
function fileNameOf(resp, fallback) {
  const cd = resp.headers['content-disposition'] || ''
  const m = cd.match(/filename\*=UTF-8''([^;]+)/i) || cd.match(/filename="?([^";]+)"?/i)
  return m ? decodeURIComponent(m[1]) : fallback
}

/**
 * 下载文件（带登录 token）。不能直接用 <a href>：浏览器打开链接时不会带 Authorization 请求头。
 * params 会拼到地址后面，比如导出时带上列表的筛选条件
 */
export async function downloadFile(url, params = {}, fallbackName = '下载文件') {
  const resp = await request.get(url, { params, responseType: 'blob', timeout: 120000 })
  const href = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = href
  a.download = fileNameOf(resp, fallbackName)
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 10000)
}

/** 取附件内容做预览（图片缩略图），返回 blob: 地址，用完要 URL.revokeObjectURL */
export async function fileObjectUrl(id) {
  const resp = await request.get(`/file/${id}`, { responseType: 'blob', timeout: 60000 })
  return URL.createObjectURL(resp.data)
}
