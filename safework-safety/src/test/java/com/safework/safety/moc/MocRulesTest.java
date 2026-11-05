package com.safework.safety.moc;

import com.safework.safety.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MocRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void actions() {
        assertTrue(MocRules.Action.SUBMIT.canFire(MocRules.DRAFT));
        assertFalse(MocRules.Action.SUBMIT.canFire(MocRules.ASSESSING));
        assertTrue(MocRules.Action.CANCEL.canFire(MocRules.APPROVING));
        // 已经开始实施的不能取消
        assertFalse(MocRules.Action.CANCEL.canFire(MocRules.IMPLEMENTING));
        assertEquals(MocRules.ASSESSING, MocRules.Action.REJECT.to);
        assertEquals(MocRules.IMPLEMENTING, MocRules.Action.PSSR_FAIL.to);
        assertTrue(MocRules.Action.RESTORE.canFire(MocRules.CLOSED));
    }

    @Test
    void approvals() {
        assertEquals(2, MocRules.approvalsNeeded(RiskLevel.MAJOR));
        assertEquals(2, MocRules.approvalsNeeded(RiskLevel.HIGH));
        assertEquals(1, MocRules.approvalsNeeded(RiskLevel.GENERAL));
        assertEquals(1, MocRules.approvalsNeeded(RiskLevel.LOW));
    }

    @Test
    void temporary() {
        assertNull(MocRules.checkTemporary(false, null, TODAY));
        assertNotNull(MocRules.checkTemporary(false, TODAY.plusDays(5), TODAY));
        assertNotNull(MocRules.checkTemporary(true, null, TODAY));
        assertNotNull(MocRules.checkTemporary(true, TODAY, TODAY));
        assertNull(MocRules.checkTemporary(true, TODAY.plusDays(1), TODAY));
        assertNull(MocRules.checkTemporary(true, TODAY.plusDays(180), TODAY));
        assertNotNull(MocRules.checkTemporary(true, TODAY.plusDays(181), TODAY));
    }

    @Test
    void pssr() {
        int n = MocRules.PSSR_ITEMS.size();
        List<Boolean> ok = new ArrayList<>(Collections.nCopies(n, true));
        assertNull(MocRules.checkPssr(ok, null));
        assertTrue(MocRules.pssrPassed(ok));
        assertNotNull(MocRules.checkPssr(ok.subList(0, n - 1), null));
        List<Boolean> missing = new ArrayList<>(ok);
        missing.set(2, null);
        assertTrue(MocRules.checkPssr(missing, null).contains("第 3 项"));
        List<Boolean> bad = new ArrayList<>(ok);
        bad.set(4, false);
        assertTrue(MocRules.checkPssr(bad, null).contains("第 5 项"));
        List<String> notes = new ArrayList<>(Collections.nCopies(n, ""));
        notes.set(4, "P&ID 还没更新");
        assertNull(MocRules.checkPssr(bad, notes));
        assertFalse(MocRules.pssrPassed(bad));
        assertNotNull(MocRules.checkPssr(null, null));
        assertNotNull(MocRules.checkPssr(Arrays.asList(new Boolean[n]), null));
    }

    @Test
    void remind() {
        assertTrue(MocRules.shouldRemindRestore(TODAY.plusDays(3), TODAY));
        assertFalse(MocRules.shouldRemindRestore(TODAY.plusDays(1), TODAY));
        assertTrue(MocRules.shouldRemindRestore(TODAY, TODAY));
        assertTrue(MocRules.shouldRemindRestore(TODAY.minusDays(1), TODAY));
        assertFalse(MocRules.shouldRemindRestore(TODAY.minusDays(2), TODAY));
        assertTrue(MocRules.shouldRemindRestore(TODAY.minusDays(8), TODAY));
    }
}
