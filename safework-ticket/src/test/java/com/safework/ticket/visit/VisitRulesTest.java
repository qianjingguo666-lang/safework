package com.safework.ticket.visit;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    private static VisitRules.Hazmat ok() {
        return new VisitRules.Hazmat("皖A12345", "王师傅", "340100123456", DAY.plusYears(1), "刘师傅", "340100654321", DAY.plusMonths(6),
                "皖交运管合字 340100001234", DAY.plusYears(2), "甲醇", 25.0);
    }

    @Test
    void idCardChecksum() {
        // 公开示例号码：11010519491231002X
        assertTrue(VisitRules.validIdCard("11010519491231002X"));
        assertTrue(VisitRules.validIdCard("11010519491231002x"));
        assertFalse(VisitRules.validIdCard("110105194912310021"));
        assertFalse(VisitRules.validIdCard("1101051949123100"));
        assertFalse(VisitRules.validIdCard(null));
    }

    @Test
    void maskKeepsFirst3AndLast4() {
        assertEquals("110***********002X", VisitRules.maskId("11010519491231002x"));
    }

    @Test
    void plateAndPhone() {
        assertTrue(VisitRules.validPlate("皖A12345"));
        assertTrue(VisitRules.validPlate("皖AD12345"));
        assertTrue(VisitRules.validPlate("皖A1234挂"));
        assertFalse(VisitRules.validPlate("皖I12345"));
        assertFalse(VisitRules.validPlate("A12345"));
        assertTrue(VisitRules.validPhone("13812345678"));
        assertFalse(VisitRules.validPhone("12812345678"));
    }

    @Test
    void hazmatAllOk() {
        assertTrue(VisitRules.hazmatProblems(ok(), DAY).isEmpty());
    }

    @Test
    void hazmatExpiredAndMissing() {
        VisitRules.Hazmat h = ok();
        VisitRules.Hazmat bad = new VisitRules.Hazmat(h.plateNo(), h.driverName(), h.driverCertNo(), DAY.minusDays(1), null, null, null,
                h.transportPermitNo(), DAY, h.cargoName(), 0.0);
        List<String> p = VisitRules.hazmatProblems(bad, DAY);
        assertEquals(3, p.size(), p.toString());
        assertTrue(p.get(0).contains("从业资格证已过期"));
    }

    @Test
    void driverAndEscortMustDiffer() {
        VisitRules.Hazmat h = ok();
        VisitRules.Hazmat same = new VisitRules.Hazmat(h.plateNo(), "王师傅", h.driverCertNo(), h.driverCertExpire(), "王师傅", h.escortCertNo(),
                h.escortCertExpire(), h.transportPermitNo(), h.transportPermitExpire(), h.cargoName(), h.cargoQty());
        assertEquals(1, VisitRules.hazmatProblems(same, DAY).size());
    }

    @Test
    void statusFlow() {
        assertEquals(VisitRules.PENDING, VisitRules.initialStatus(VisitRules.HAZMAT));
        assertEquals(VisitRules.APPROVED, VisitRules.initialStatus(VisitRules.VISITOR));
        assertTrue(VisitRules.Action.ENTER.canFire(VisitRules.APPROVED));
        assertFalse(VisitRules.Action.ENTER.canFire(VisitRules.PENDING));
        assertFalse(VisitRules.Action.CANCEL.canFire(VisitRules.ENTERED));
        assertTrue(VisitRules.Action.LEAVE.canFire(VisitRules.ENTERED));
    }

    @Test
    void days() {
        assertNull(VisitRules.checkEnterDay(DAY, DAY));
        assertNotNull(VisitRules.checkEnterDay(DAY.plusDays(1), DAY));
        assertNotNull(VisitRules.checkEnterDay(DAY.minusDays(1), DAY));
        assertNull(VisitRules.checkPlanDate(DAY.plusDays(30), DAY));
        assertNotNull(VisitRules.checkPlanDate(DAY.plusDays(31), DAY));
        assertNotNull(VisitRules.checkPlanDate(DAY.minusDays(1), DAY));
        assertTrue(VisitRules.overstay(VisitRules.ENTERED, DAY.minusDays(1), DAY));
        assertFalse(VisitRules.overstay(VisitRules.ENTERED, DAY, DAY));
        assertFalse(VisitRules.overstay(VisitRules.LEFT, DAY.minusDays(1), DAY));
    }
}
