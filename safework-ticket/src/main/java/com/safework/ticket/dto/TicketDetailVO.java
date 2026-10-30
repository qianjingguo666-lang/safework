package com.safework.ticket.dto;

import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.entity.WorkTicketLog;
import com.safework.ticket.enums.TicketAction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TicketDetailVO {
    private WorkTicket ticket;
    /** 操作记录，按时间正序 */
    private List<WorkTicketLog> logs;
    /** 当前登录用户对这张票可以执行的动作，前端据此显示按钮 */
    private List<TicketAction> actions;
    /** 审批进度（没提交过为 null） */
    private com.safework.ticket.approval.ApprovalProgressVO approval;
}
