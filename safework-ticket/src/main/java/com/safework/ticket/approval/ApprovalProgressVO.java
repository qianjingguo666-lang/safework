package com.safework.ticket.approval;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批进度（当前这一次提交）
 *
 * @param currentNode 当前节点序号，审批结束为 null
 */
public record ApprovalProgressVO(int round, int totalNodes, Integer currentNode, List<Node> nodes) {

    /**
     * @param status WAITING 未开始 / ACTIVE 审批中 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已取消
     */
    public record Node(int index, String name, String mode, String approverDesc, String status, List<Task> tasks) {
    }

    public record Task(String approverName, String status, String comment,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime decideTime) {
    }
}
