package com.safework.safety.equipment;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    @Test
    void cycle() {
        assertEquals(6, EquipmentRules.cycle("PRESSURE_GAUGE", null));
        assertEquals(3, EquipmentRules.cycle("PRESSURE_GAUGE", 3));
        assertEquals(36, EquipmentRules.cycle("PRESSURE_VESSEL", null));
        boolean thrown = false;
        try {
            // 压力表最长半年
            EquipmentRules.cycle("PRESSURE_GAUGE", 12);
        } catch (IllegalArgumentException e) {
            thrown = e.getMessage().contains("1 ~ 6");
        }
        assertTrue(thrown);
        thrown = false;
        try {
            EquipmentRules.cycle("SPACESHIP", null);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
        assertEquals(LocalDate.of(2027, 4, 30), EquipmentRules.nextInspect(TODAY, 6));
    }

    @Test
    void state() {
        assertEquals(EquipmentRules.NOT_IN_USE, EquipmentRules.state(EquipmentRules.STOPPED, TODAY.minusDays(1), TODAY));
        assertEquals(EquipmentRules.NEVER, EquipmentRules.state(EquipmentRules.IN_USE, null, TODAY));
        assertEquals(EquipmentRules.OVERDUE, EquipmentRules.state(EquipmentRules.IN_USE, TODAY.minusDays(1), TODAY));
        assertEquals(EquipmentRules.DUE_SOON, EquipmentRules.state(EquipmentRules.IN_USE, TODAY, TODAY));
        assertEquals(EquipmentRules.DUE_SOON, EquipmentRules.state(EquipmentRules.IN_USE, TODAY.plusDays(30), TODAY));
        assertEquals(EquipmentRules.OK, EquipmentRules.state(EquipmentRules.IN_USE, TODAY.plusDays(31), TODAY));
    }

    @Test
    void statusChange() {
        assertNull(EquipmentRules.checkStatusChange(EquipmentRules.IN_USE, EquipmentRules.STOPPED, EquipmentRules.PASS, TODAY, TODAY));
        assertNull(EquipmentRules.checkStatusChange(EquipmentRules.STOPPED, EquipmentRules.IN_USE, EquipmentRules.PASS, TODAY.plusDays(10), TODAY));
        assertNotNull(EquipmentRules.checkStatusChange(EquipmentRules.STOPPED, EquipmentRules.IN_USE, EquipmentRules.FAIL, TODAY.plusDays(10), TODAY));
        assertNotNull(EquipmentRules.checkStatusChange(EquipmentRules.STOPPED, EquipmentRules.IN_USE, EquipmentRules.PASS, TODAY.minusDays(1), TODAY));
        assertNotNull(EquipmentRules.checkStatusChange(EquipmentRules.SCRAPPED, EquipmentRules.IN_USE, null, null, TODAY));
        assertNotNull(EquipmentRules.checkStatusChange(EquipmentRules.IN_USE, EquipmentRules.IN_USE, null, null, TODAY));
        assertNull(EquipmentRules.checkStatusChange(EquipmentRules.STOPPED, EquipmentRules.SCRAPPED, EquipmentRules.FAIL, null, TODAY));
    }

    @Test
    void remind() {
        assertTrue(EquipmentRules.shouldRemind(TODAY.plusDays(30), TODAY));
        assertTrue(EquipmentRules.shouldRemind(TODAY.plusDays(7), TODAY));
        assertTrue(EquipmentRules.shouldRemind(TODAY, TODAY));
        assertFalse(EquipmentRules.shouldRemind(TODAY.plusDays(8), TODAY));
        assertFalse(EquipmentRules.shouldRemind(TODAY.minusDays(1), TODAY));
        assertTrue(EquipmentRules.shouldRemind(TODAY.minusDays(7), TODAY));
        assertTrue(EquipmentRules.shouldRemind(TODAY.minusDays(14), TODAY));
        assertFalse(EquipmentRules.shouldRemind(null, TODAY));
    }

    @Test
    void hazardLevel() {
        assertEquals("MAJOR", EquipmentRules.hazardLevel("SAFETY_VALVE"));
        assertEquals("MAJOR", EquipmentRules.hazardLevel("TOXIC_DETECTOR"));
        assertEquals("GENERAL", EquipmentRules.hazardLevel("EXTINGUISHER"));
    }
}
