/** 金额显示：元，千分位两位小数 */
export const yuan = (v) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
/** 金额显示：万元，两位小数 */
export const wan = (v) => (v == null ? '-' : (Number(v) / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
export const STATE_TAGS = { OVER: { label: '超预算', type: 'danger' }, UNPLANNED: { label: '无预算支出', type: 'warning' } }
