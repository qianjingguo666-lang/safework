package com.safework.safety.check;

/**
 * 检查表里的一个检查项
 *
 * @param content  检查内容，如“储罐液位、压力、温度在正常范围”
 * @param standard 检查标准 / 方法（可选），如“对照 DCS 显示和现场仪表”
 * @param category 不合格时转隐患用的类别（设备设施 / 作业环境 / 人员行为 / 安全管理），可选
 */
public record CheckItem(String content, String standard, String category) {
}
