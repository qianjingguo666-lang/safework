export const VISIT_TYPES = { VISITOR: { label: '访客', type: '' }, VEHICLE: { label: '普通车辆', type: 'info' }, HAZMAT: { label: '危化品车辆', type: 'danger' } }
export const VISIT_STATUS = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '待入厂', type: 'primary' },
  ENTERED: { label: '在厂', type: 'success' },
  LEFT: { label: '已离厂', type: 'info' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' }
}
export const hm = (t) => (t ? t.slice(11, 16) : '')
