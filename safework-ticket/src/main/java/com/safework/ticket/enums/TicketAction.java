package com.safework.ticket.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

import static com.safework.ticket.enums.TicketStatus.APPROVED;
import static com.safework.ticket.enums.TicketStatus.CANCELLED;
import static com.safework.ticket.enums.TicketStatus.COMPLETED;
import static com.safework.ticket.enums.TicketStatus.DRAFT;
import static com.safework.ticket.enums.TicketStatus.IN_PROGRESS;
import static com.safework.ticket.enums.TicketStatus.PENDING;
import static com.safework.ticket.enums.TicketStatus.REJECTED;

/**
 * 作业票状态机：每个动作允许从哪些状态出发、流转到哪个状态，全部集中定义在这里。
 * 业务代码不能直接改 status，只能通过动作流转，防止跳步（比如草稿直接变成作业中）。
 *
 * <pre>
 *  DRAFT ──提交──▶ PENDING ──批准──▶ APPROVED ──开工──▶ IN_PROGRESS ──完工──▶ COMPLETED
 *    ▲               │
 *    │              驳回
 *    │               ▼
 *    └──修改后提交── REJECTED          （DRAFT / PENDING / REJECTED / APPROVED 可取消 → CANCELLED）
 * </pre>
 */
@Getter
public enum TicketAction {
    CREATE("创建", EnumSet.noneOf(TicketStatus.class), DRAFT),
    EDIT("修改", EnumSet.of(DRAFT, REJECTED), null),
    SUBMIT("提交审批", EnumSet.of(DRAFT, REJECTED), PENDING),
    APPROVE("审批通过", EnumSet.of(PENDING), APPROVED),
    REJECT("驳回", EnumSet.of(PENDING), REJECTED),
    START("开始作业", EnumSet.of(APPROVED), IN_PROGRESS),
    COMPLETE("作业完成", EnumSet.of(IN_PROGRESS), COMPLETED),
    CANCEL("取消", EnumSet.of(DRAFT, PENDING, REJECTED, APPROVED), CANCELLED);

    private final String label;
    private final Set<TicketStatus> from;
    /** 流转后的状态；null 表示不改变状态（如修改） */
    private final TicketStatus to;

    TicketAction(String label, Set<TicketStatus> from, TicketStatus to) {
        this.label = label;
        this.from = from;
        this.to = to;
    }

    public boolean canFire(TicketStatus current) {
        return from.contains(current);
    }
}
