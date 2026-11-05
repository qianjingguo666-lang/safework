package com.safework.safety.ppe;

import java.time.LocalDate;
import java.util.List;

/** 劳保用品的返回对象 */
public final class PpeVOs {

    private PpeVOs() {
    }

    /** 岗位 + 人员 + 发放标准 */
    public record Post(Long id, String name, String remark, Integer status, List<Member> members, List<Standard> standards) {
    }

    public record Member(Long userId, String userName) {
    }

    public record Standard(Long itemId, String itemName, String unit, Integer validMonths, Integer quantity) {
    }

    /**
     * 台账一行：某人应配的某种用品现在的状态
     *
     * @param daysLeft 距到期天数，负数是已过期；未发放为空
     */
    public record LedgerRow(Long userId, String userName, String posts, Long itemId, String itemName, String category, String unit,
                            Integer quantity, String state, LocalDate issueDate, LocalDate expireDate, Long daysLeft) {
    }

    public record LedgerPage(List<LedgerRow> records, long total) {
    }

    /**
     * 统计
     *
     * @param people   岗位上的人数
     * @param missing  未发放（人 × 用品）
     * @param expired  已过期
     * @param dueSoon  30 天内到期
     * @param lowStock 库存低于安全库存的用品种数
     */
    public record Overview(int people, int missing, int expired, int dueSoon, int lowStock) {
    }

    /** 批量补发结果 */
    public record BatchResult(int people, int records, List<String> lines) {
    }
}
