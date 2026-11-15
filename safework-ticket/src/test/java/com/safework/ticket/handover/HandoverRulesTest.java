package com.safework.ticket.handover;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HandoverRulesTest {
    @Test void requiresEachDistinctItem() {
        assertFalse(HandoverRules.allChecked(List.of("siteCondition","unfinished","unfinished")));
        assertFalse(HandoverRules.allChecked(List.of("siteCondition","unfinished")));
        assertTrue(HandoverRules.allChecked(List.of("recheck","unfinished","siteCondition")));
    }
}
