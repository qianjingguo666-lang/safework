package com.safework.ticket.approval;

import com.safework.ticket.enums.TicketType;

import java.util.List;

/**
 * 流程配置（页面展示）
 *
 * @param configured false 表示没有配置，用的是默认流程
 * @param approvers  每个节点现在能审批的人（预览，方便发现“没人能审批”的节点）
 */
public record FlowVO(TicketType ticketType, String typeLabel, boolean configured, String updaterName,
                     List<FlowNode> nodes, List<List<String>> approvers) {
}
