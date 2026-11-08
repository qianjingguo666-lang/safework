package com.safework.ticket.jsa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsaRulesTest {

    private static JsaRules.Step step(Integer l, Integer s, String controls) {
        return new JsaRules.Step("进入调节池清淤", "硫化氢中毒", l, s, controls);
    }

    @Test
    void riskLevels() {
        assertEquals("LOW", JsaRules.level(4));
        assertEquals("GENERAL", JsaRules.level(5));
        assertEquals("GENERAL", JsaRules.level(9));
        assertEquals("HIGH", JsaRules.level(10));
        assertEquals("HIGH", JsaRules.level(14));
        assertEquals("MAJOR", JsaRules.level(15));
        assertEquals(20, JsaRules.risk(step(4, 5, "a")));
        assertEquals(12, JsaRules.maxRisk(List.of(step(2, 3, "a"), step(3, 4, "b"))));
        assertEquals(0, JsaRules.maxRisk(List.of()));
    }

    @Test
    void checkSteps() {
        List<JsaRules.Step> ok = new ArrayList<>(List.of(step(2, 3, "通风"), step(3, 3, "检测")));
        assertNull(JsaRules.checkSteps(ok));
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "通风"))), "至少 2 步");
        assertNotNull(JsaRules.checkSteps(null));
        List<JsaRules.Step> many = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            many.add(step(1, 1, "x"));
        }
        assertNotNull(JsaRules.checkSteps(many), "最多 30 步");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), new JsaRules.Step(" ", "h", 1, 1, "c"))), "没写步骤");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), new JsaRules.Step("s", "", 1, 1, "c"))), "没写危害");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(0, 3, "c"))), "L 不能是 0");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(2, 6, "c"))), "S 不能是 6");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(2, null, "c"))), "S 没填");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(2, 3, ""))), "没写措施");
        assertNotNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(4, 4, "强制通风"))), "重大风险措施只有 1 条");
        assertNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(4, 4, "强制通风；三项气体检测合格"))));
        assertNull(JsaRules.checkSteps(List.of(step(2, 3, "a"), step(4, 4, "强制通风\n佩戴空气呼吸器"))));
    }

    @Test
    void measures() {
        assertEquals(0, JsaRules.measures(null));
        assertEquals(1, JsaRules.measures("强制通风"));
        assertEquals(3, JsaRules.measures("强制通风；检测合格;监护人在场。"));
        assertEquals(2, JsaRules.measures("强制通风\n\n检测合格"));
    }

    @Test
    void actionsAndUsable() {
        assertTrue(JsaRules.Action.SUBMIT.canFire(JsaRules.DRAFT));
        assertTrue(JsaRules.Action.SUBMIT.canFire(JsaRules.REJECTED));
        assertFalse(JsaRules.Action.SUBMIT.canFire(JsaRules.PUBLISHED));
        assertFalse(JsaRules.Action.APPROVE.canFire(JsaRules.DRAFT));
        assertTrue(JsaRules.Action.ARCHIVE.canFire(JsaRules.PUBLISHED));
        assertTrue(JsaRules.editable(JsaRules.REJECTED));
        assertFalse(JsaRules.editable(JsaRules.REVIEWING));
        assertNull(JsaRules.checkUsable(JsaRules.PUBLISHED, "HOT_WORK", "HOT_WORK"));
        assertNull(JsaRules.checkUsable(JsaRules.PUBLISHED, null, "HEIGHT"), "通用 JSA");
        assertNotNull(JsaRules.checkUsable(JsaRules.PUBLISHED, "HOT_WORK", "HEIGHT"));
        assertNotNull(JsaRules.checkUsable(JsaRules.REVIEWING, null, "HEIGHT"));
        assertNotNull(JsaRules.checkUsable(JsaRules.ARCHIVED, null, "HEIGHT"));
    }

    @Test
    void reviewDue() {
        LocalDateTime p = LocalDateTime.of(2025, 11, 7, 10, 0);
        assertFalse(JsaRules.reviewDue(JsaRules.PUBLISHED, p, LocalDate.of(2026, 11, 6)));
        assertTrue(JsaRules.reviewDue(JsaRules.PUBLISHED, p, LocalDate.of(2026, 11, 7)));
        assertFalse(JsaRules.reviewDue(JsaRules.ARCHIVED, p, LocalDate.of(2027, 1, 1)));
        assertFalse(JsaRules.reviewDue(JsaRules.PUBLISHED, null, LocalDate.of(2027, 1, 1)));
    }
}
