package com.safework.ai.core.hazard;

/**
 * 从一句话里提取出的隐患草稿（还没有提交，等上报人核对）
 *
 * @param category   设备设施 / 作业环境 / 人员行为 / 安全管理
 * @param level      GENERAL 一般隐患 / MAJOR 重大隐患
 * @param spaceCode  匹配到的受限空间编号，没有为 null
 * @param levelReason 为什么判定为这个等级
 */
public record HazardDraft(String title, String category, String level, String location, String spaceCode,
                          String description, String levelReason) {
}
