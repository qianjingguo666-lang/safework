package com.safework.ticket.approval;

import java.util.List;
import java.util.Set;

/** 审批流程的规则（纯计算，方便单元测试） */
public final class ApprovalRules {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** 或签节点有人同意后，其他人的待办自动取消 */
    public static final String CANCELLED = "CANCELLED";

    public static final int MAX_NODES = 5;
    public static final int MAX_USERS = 20;

    /** 没有配置流程时的默认流程：一个节点，有审批权限的任何人（除申请人）都可以审批 —— 和以前的行为一致 */
    public static final List<FlowNode> DEFAULT_FLOW = List.of(
            new FlowNode("安全员审批", FlowNode.ANY_APPROVER, null, null, FlowNode.MODE_ANY));

    private static final Set<String> TYPES = Set.of(FlowNode.ANY_APPROVER, FlowNode.ROLE, FlowNode.USER);
    private static final Set<String> MODES = Set.of(FlowNode.MODE_ANY, FlowNode.MODE_ALL);

    private ApprovalRules() {
    }

    /** 校验流程配置，通过返回 null */
    public static String validate(List<FlowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "至少要有 1 个审批节点";
        }
        if (nodes.size() > MAX_NODES) {
            return "审批节点不能超过 " + MAX_NODES + " 个，流程太长会耽误作业";
        }
        for (int i = 0; i < nodes.size(); i++) {
            FlowNode n = nodes.get(i);
            String at = "第 " + (i + 1) + " 个节点";
            if (n == null || n.name() == null || n.name().isBlank()) {
                return at + "没有名称";
            }
            if (n.name().length() > 20) {
                return at + "名称不能超过 20 字";
            }
            if (!TYPES.contains(n.approverType())) {
                return at + "【" + n.name() + "】审批人类型不正确";
            }
            if (!MODES.contains(n.mode())) {
                return at + "【" + n.name() + "】请选择或签还是会签";
            }
            if (FlowNode.ROLE.equals(n.approverType()) && (n.roleCode() == null || n.roleCode().isBlank())) {
                return at + "【" + n.name() + "】请选择审批角色";
            }
            if (FlowNode.USER.equals(n.approverType())) {
                if (n.userIds() == null || n.userIds().isEmpty()) {
                    return at + "【" + n.name() + "】请选择审批人";
                }
                if (n.userIds().size() > MAX_USERS) {
                    return at + "【" + n.name() + "】审批人不能超过 " + MAX_USERS + " 个";
                }
            }
            // 会签要求每个人都同意：审批人范围必须是明确的一组人，不能是“所有有审批权限的人”
            if (FlowNode.MODE_ALL.equals(n.mode()) && FlowNode.ANY_APPROVER.equals(n.approverType())) {
                return at + "【" + n.name() + "】会签只能用于指定角色或指定人员";
            }
        }
        return null;
    }

    /**
     * 节点结果：任何人驳回 → 驳回；或签有一人同意 → 通过；会签全部同意 → 通过；否则还在审批中。
     * 已取消的待办不参与计算。
     */
    public static String outcome(String mode, List<String> taskStatuses) {
        List<String> active = taskStatuses.stream().filter(s -> !CANCELLED.equals(s)).toList();
        if (active.contains(REJECTED)) {
            return REJECTED;
        }
        if (FlowNode.MODE_ANY.equals(mode)) {
            return active.contains(APPROVED) ? APPROVED : PENDING;
        }
        return !active.isEmpty() && active.stream().allMatch(APPROVED::equals) ? APPROVED : PENDING;
    }
}
