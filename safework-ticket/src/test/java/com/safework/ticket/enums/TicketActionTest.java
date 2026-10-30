package com.safework.ticket.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("作业票状态机")
class TicketActionTest {

    @Test
    @DisplayName("正常流程：草稿 → 待审批 → 已批准 → 作业中 → 已完成")
    void happyPath() {
        TicketStatus s = TicketStatus.DRAFT;
        for (TicketAction a : new TicketAction[]{TicketAction.SUBMIT, TicketAction.APPROVE, TicketAction.START, TicketAction.COMPLETE}) {
            assertTrue(a.canFire(s), a + " 应该可以从 " + s + " 执行");
            s = a.getTo();
        }
        assertEquals(TicketStatus.COMPLETED, s);
    }

    @Test
    @DisplayName("不能跳步：草稿不能直接审批，待审批不能直接开工")
    void noSkipping() {
        assertFalse(TicketAction.APPROVE.canFire(TicketStatus.DRAFT));
        assertFalse(TicketAction.START.canFire(TicketStatus.PENDING));
        assertFalse(TicketAction.COMPLETE.canFire(TicketStatus.APPROVED));
    }

    @Test
    @DisplayName("驳回后可以修改、重新提交")
    void rejectAndResubmit() {
        assertEquals(TicketStatus.REJECTED, TicketAction.REJECT.getTo());
        assertTrue(TicketAction.EDIT.canFire(TicketStatus.REJECTED));
        assertTrue(TicketAction.SUBMIT.canFire(TicketStatus.REJECTED));
        assertNull(TicketAction.EDIT.getTo(), "修改不改变状态");
    }

    @Test
    @DisplayName("作业中、已完成、已取消都不能再取消")
    void cancelRules() {
        assertTrue(TicketAction.CANCEL.canFire(TicketStatus.APPROVED));
        assertFalse(TicketAction.CANCEL.canFire(TicketStatus.IN_PROGRESS));
        assertFalse(TicketAction.CANCEL.canFire(TicketStatus.COMPLETED));
        assertFalse(TicketAction.CANCEL.canFire(TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态（已完成、已取消）不能再执行任何动作")
    void terminalStates() {
        for (TicketAction a : TicketAction.values()) {
            assertFalse(a.canFire(TicketStatus.COMPLETED), a.name());
            assertFalse(a.canFire(TicketStatus.CANCELLED), a.name());
        }
    }
}
