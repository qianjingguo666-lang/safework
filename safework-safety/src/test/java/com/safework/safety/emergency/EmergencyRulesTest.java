package com.safework.safety.emergency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("应急管理规则：事故等级、事件状态、演练周期")
class EmergencyRulesTest {

    @Test
    @DisplayName("伤亡人数决定最低等级，边界值")
    void minLevel() {
        assertEquals("GENERAL", EmergencyRules.minLevel(0, 0));
        assertEquals("GENERAL", EmergencyRules.minLevel(2, 9));
        assertEquals("LARGER", EmergencyRules.minLevel(3, 0));
        assertEquals("LARGER", EmergencyRules.minLevel(0, 10));
        assertEquals("MAJOR", EmergencyRules.minLevel(10, 0));
        assertEquals("MAJOR", EmergencyRules.minLevel(0, 50));
        assertEquals("EXTRA", EmergencyRules.minLevel(30, 0));
        assertEquals("EXTRA", EmergencyRules.minLevel(0, 100));
    }

    @Test
    @DisplayName("上报等级不能报低：低了自动调高，报高保留")
    void effectiveLevel() {
        assertEquals("LARGER", EmergencyRules.effectiveLevel("GENERAL", 3, 0));
        assertEquals("MAJOR", EmergencyRules.effectiveLevel("MAJOR", 0, 0));
        assertEquals("GENERAL", EmergencyRules.effectiveLevel("GENERAL", 1, 2));
    }

    @Test
    @DisplayName("状态流转：已上报才能启动响应，响应中才能追加记录，已结束不能再操作")
    void actions() {
        assertTrue(EmergencyRules.Action.START.canFire(EmergencyRules.REPORTED));
        assertFalse(EmergencyRules.Action.START.canFire(EmergencyRules.RESPONDING));
        assertTrue(EmergencyRules.Action.UPDATE.canFire(EmergencyRules.RESPONDING));
        assertFalse(EmergencyRules.Action.UPDATE.canFire(EmergencyRules.REPORTED));
        assertTrue(EmergencyRules.Action.CLOSE.canFire(EmergencyRules.REPORTED));
        assertFalse(EmergencyRules.Action.CLOSE.canFire(EmergencyRules.CLOSED));
    }

    @Test
    @DisplayName("演练周期：现场处置方案半年、其他一年；30 天内到期提醒")
    void drill() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        assertEquals(EmergencyRules.DRILL_NEVER, EmergencyRules.drillState("SPECIAL", null, today).state());
        assertNull(EmergencyRules.drillState("SPECIAL", null, today).nextDue());
        EmergencyRules.DrillState s = EmergencyRules.drillState("ONSITE", LocalDateTime.of(2026, 4, 1, 9, 0), today);
        assertEquals(EmergencyRules.DRILL_OVERDUE, s.state());
        assertEquals(LocalDate.of(2026, 10, 1), s.nextDue());
        assertEquals(EmergencyRules.DRILL_OK, EmergencyRules.drillState("SPECIAL", LocalDateTime.of(2026, 4, 1, 9, 0), today).state());
        assertEquals(EmergencyRules.DRILL_DUE_SOON, EmergencyRules.drillState("COMPREHENSIVE", LocalDateTime.of(2025, 11, 20, 9, 0), today).state());
        assertEquals(EmergencyRules.DRILL_DUE_SOON, EmergencyRules.drillState("ONSITE", LocalDateTime.of(2026, 5, 20, 9, 0), today).state());
    }
}
