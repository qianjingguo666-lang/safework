package com.safework.ticket.approval;

import java.util.List;

/**
 * 审批流程里的一个节点
 *
 * @param name         节点名称，如“安全员审核”
 * @param approverType ANY_APPROVER 所有有审批权限的人 / ROLE 指定角色 / USER 指定人员
 * @param roleCode     approverType = ROLE 时的角色编码
 * @param userIds      approverType = USER 时的人员
 * @param mode         ANY 或签（一人同意即通过）/ ALL 会签（所有人都同意才通过）
 */
public record FlowNode(String name, String approverType, String roleCode, List<Long> userIds, String mode) {

    public static final String ANY_APPROVER = "ANY_APPROVER";
    public static final String ROLE = "ROLE";
    public static final String USER = "USER";
    public static final String MODE_ANY = "ANY";
    public static final String MODE_ALL = "ALL";
}
