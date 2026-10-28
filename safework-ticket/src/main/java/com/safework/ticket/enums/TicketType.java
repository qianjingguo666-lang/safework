package com.safework.ticket.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 作业类型 */
@Getter
@AllArgsConstructor
public enum TicketType {
    CONFINED_SPACE("SX", "受限空间作业", true),
    HOT_WORK("DH", "动火作业", true),
    HEIGHT("GC", "高处作业", false);

    /** 作业票编号前缀 */
    private final String prefix;
    private final String label;
    /** 是否必须做气体检测 */
    private final boolean gasRequired;
}
