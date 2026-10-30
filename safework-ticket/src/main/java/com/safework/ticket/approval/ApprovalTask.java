package com.safework.ticket.approval;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 审批待办：节点激活时给每个审批人生成一条 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_approval_task")
public class ApprovalTask extends BaseEntity {
    private Long ticketId;
    /** 第几次提交 */
    private Integer roundNo;
    private Integer nodeIndex;
    private String nodeName;
    private String mode;
    private Long approverId;
    private String approverName;
    /** PENDING 待审批 / APPROVED 同意 / REJECTED 驳回 / CANCELLED 已取消（或签已有人同意、票被驳回或取消） */
    private String status;
    private String comment;
    private LocalDateTime decideTime;
}
