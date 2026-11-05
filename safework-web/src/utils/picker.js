// 日期范围选择器的弹出层：屏幕不够高时（笔记本 125% 缩放、浏览器窗口不大），默认往下弹会超出屏幕，“确定”按钮点不到。
// 允许弹出层沿上下方向挪动、必要时盖住输入框，保证整个面板都在屏幕里。
export const PICKER_POPPER = {
  modifiers: [
    { name: 'flip', options: { fallbackPlacements: ['top-start', 'bottom-start'] } },
    { name: 'preventOverflow', options: { altAxis: true, tether: false, padding: 8 } }
  ]
}
