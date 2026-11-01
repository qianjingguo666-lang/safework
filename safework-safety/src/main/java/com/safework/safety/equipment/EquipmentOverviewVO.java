package com.safework.safety.equipment;

/**
 * @param inUse   在用设备
 * @param overdue 在用但检验逾期
 * @param dueSoon 30 天内要检验
 * @param never   在用但没有检验记录
 * @param stopped 停用
 */
public record EquipmentOverviewVO(long inUse, long overdue, long dueSoon, long never, long stopped) {
}
