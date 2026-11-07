export const GRADE_TYPES = { 一级: 'success', 二级: 'primary', 三级: 'warning', 未达标: 'danger' }
export const fmt = (v) => (v == null ? '-' : Number.isInteger(Number(v)) ? String(Number(v)) : String(v))
