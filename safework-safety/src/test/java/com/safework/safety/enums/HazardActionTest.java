package com.safework.safety.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("隐患闭环状态机")
class HazardActionTest {

    @Test
    @DisplayName("上报 → 指派 → 整改 → 验收通过，形成闭环")
    void closedLoop() {
        HazardStatus s = HazardAction.REPORT.getTo();
        assertEquals(HazardStatus.REPORTED, s);
        for (HazardAction a : new HazardAction[]{HazardAction.ASSIGN, HazardAction.RECTIFY, HazardAction.VERIFY_PASS}) {
            assertTrue(a.canFire(s), a + " 应该可以从 " + s + " 执行");
            s = a.getTo();
        }
        assertEquals(HazardStatus.CLOSED, s);
    }

    @Test
    @DisplayName("验收不通过退回整改中，可以再次提交整改")
    void verifyFail() {
        assertEquals(HazardStatus.RECTIFYING, HazardAction.VERIFY_FAIL.getTo());
        assertTrue(HazardAction.RECTIFY.canFire(HazardAction.VERIFY_FAIL.getTo()));
    }

    @Test
    @DisplayName("只有待指派的隐患可以撤销；整改中不能直接验收")
    void guards() {
        assertTrue(HazardAction.REVOKE.canFire(HazardStatus.REPORTED));
        assertFalse(HazardAction.REVOKE.canFire(HazardStatus.RECTIFYING));
        assertFalse(HazardAction.VERIFY_PASS.canFire(HazardStatus.RECTIFYING));
    }

    @Test
    @DisplayName("已闭环、已撤销不能再执行任何动作")
    void terminal() {
        for (HazardAction a : HazardAction.values()) {
            assertFalse(a.canFire(HazardStatus.CLOSED), a.name());
            assertFalse(a.canFire(HazardStatus.REVOKED), a.name());
        }
    }
}
