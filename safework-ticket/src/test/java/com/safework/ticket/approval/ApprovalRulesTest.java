package com.safework.ticket.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.safework.ticket.approval.ApprovalRules.APPROVED;
import static com.safework.ticket.approval.ApprovalRules.CANCELLED;
import static com.safework.ticket.approval.ApprovalRules.PENDING;
import static com.safework.ticket.approval.ApprovalRules.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("审批流程规则")
class ApprovalRulesTest {

    private static FlowNode node(String name, String type, String role, List<Long> users, String mode) {
        return new FlowNode(name, type, role, users, mode);
    }

    @Test
    @DisplayName("或签：一人同意即通过；还没人处理时审批中")
    void anyMode() {
        assertEquals(APPROVED, ApprovalRules.outcome("ANY", List.of(PENDING, APPROVED, PENDING)));
        assertEquals(PENDING, ApprovalRules.outcome("ANY", List.of(PENDING, PENDING)));
    }

    @Test
    @DisplayName("会签：全部同意才通过；取消的不算；任何人驳回直接驳回")
    void allMode() {
        assertEquals(PENDING, ApprovalRules.outcome("ALL", List.of(APPROVED, PENDING)));
        assertEquals(APPROVED, ApprovalRules.outcome("ALL", List.of(APPROVED, APPROVED)));
        assertEquals(APPROVED, ApprovalRules.outcome("ALL", List.of(APPROVED, CANCELLED)));
        assertEquals(REJECTED, ApprovalRules.outcome("ALL", List.of(APPROVED, REJECTED, PENDING)));
        assertEquals(REJECTED, ApprovalRules.outcome("ANY", List.of(REJECTED, PENDING)));
        assertEquals(PENDING, ApprovalRules.outcome("ALL", List.of()));
    }

    @Test
    @DisplayName("默认流程：一个节点、有审批权限的人或签，和改造前的行为一致")
    void defaultFlow() {
        assertNull(ApprovalRules.validate(ApprovalRules.DEFAULT_FLOW));
        assertEquals(1, ApprovalRules.DEFAULT_FLOW.size());
        assertEquals(FlowNode.ANY_APPROVER, ApprovalRules.DEFAULT_FLOW.get(0).approverType());
    }

    @Test
    @DisplayName("配置校验：节点数量、名称、角色、人员、会签范围")
    void validate() {
        assertNull(ApprovalRules.validate(List.of(
                node("安全员审核", "ROLE", "safety", null, "ANY"),
                node("负责人会签", "USER", null, List.of(1L, 2L), "ALL"))));
        assertTrue(ApprovalRules.validate(List.of()).contains("至少"));
        assertTrue(ApprovalRules.validate(Collections.nCopies(6, node("a", "ANY_APPROVER", null, null, "ANY"))).contains("不能超过 5"));
        assertTrue(ApprovalRules.validate(List.of(node(" ", "ROLE", "safety", null, "ANY"))).contains("没有名称"));
        assertTrue(ApprovalRules.validate(List.of(node("审核", "ROLE", null, null, "ANY"))).contains("请选择审批角色"));
        assertTrue(ApprovalRules.validate(List.of(node("审核", "USER", null, List.of(), "ANY"))).contains("请选择审批人"));
        assertTrue(ApprovalRules.validate(List.of(node("审核", "ANY_APPROVER", null, null, "ALL"))).contains("会签只能用于"));
        assertTrue(ApprovalRules.validate(List.of(node("审核", "BOSS", null, null, "ANY"))).contains("类型不正确"));
    }
}
