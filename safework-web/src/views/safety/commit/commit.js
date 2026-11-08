export const ASPECTS = { PRODUCTION: '生产负责人', EQUIPMENT: '设备负责人', PROCESS: '工艺负责人', SAFETY: '安全负责人' }
export const STAT_LABELS = [
  ['hotWork', '动火作业'], ['confined', '受限空间作业'], ['height', '高处作业'], ['majorSources', '重大危险源'],
  ['overstock', '超储'], ['openMajorHazards', '未整改重大隐患'], ['openHazards', '未整改隐患'], ['mocTemporary', '未恢复临时变更']
]
export const HISTORY_STATUS = {
  COMMITTED: { label: '已承诺', type: 'success' },
  JUDGING: { label: '研判中', type: 'warning' },
  NONE: { label: '未研判', type: 'danger' }
}
