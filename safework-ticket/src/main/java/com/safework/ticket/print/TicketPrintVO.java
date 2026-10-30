package com.safework.ticket.print;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.safework.ticket.approval.ApprovalProgressVO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.entity.WorkTicketLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打印作业票需要的全部数据
 *
 * @param logs        操作记录（带手写签名图片）
 * @param verifyToken 二维码验真码，二维码内容是 /verify/ticket/{verifyToken}
 * @param verdict     现在这张票能不能作业（打印在票面上，草稿、作废等加水印）
 */
public record TicketPrintVO(WorkTicket ticket, List<WorkTicketLog> logs, ApprovalProgressVO approval,
                            String tenantName, String verifyToken, TicketVerdict verdict, String printedBy,
                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime printTime) {
}
