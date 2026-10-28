package com.safework.ticket.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 作业票状态 */
@Getter
@AllArgsConstructor
public enum TicketStatus {
    DRAFT("草稿"),
    PENDING("待审批"),
    APPROVED("已批准"),
    IN_PROGRESS("作业中"),
    COMPLETED("已完成"),
    REJECTED("已驳回"),
    CANCELLED("已取消");

    private final String label;
}
