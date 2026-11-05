package com.safework.safety.meeting;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void fit() {
        assertTrue(MeetingRules.fit("PRESENT", "GOOD"));
        assertFalse(MeetingRules.fit("PRESENT", "DRINK"));
        assertFalse(MeetingRules.fit("LEAVE", null));
        assertTrue(MeetingRules.unfit("PRESENT", "TIRED"));
        // 请假的人不算“状态不好”
        assertFalse(MeetingRules.unfit("LEAVE", null));
    }

    @Test
    void dateAndTopics() {
        assertNull(MeetingRules.checkDate(TODAY, TODAY));
        assertNull(MeetingRules.checkDate(TODAY.minusDays(3), TODAY));
        assertNotNull(MeetingRules.checkDate(TODAY.minusDays(4), TODAY));
        assertNotNull(MeetingRules.checkDate(TODAY.plusDays(1), TODAY));
        assertNotNull(MeetingRules.checkDate(null, TODAY));
        assertNotNull(MeetingRules.checkTopics("注意安全"));
        assertNull(MeetingRules.checkTopics("今天罐区动火作业，注意可燃气体检测"));
        assertNotNull(MeetingRules.checkTopics("x".repeat(2001)));
    }

    @Test
    void roll() {
        Set<Long> members = Set.of(1L, 2L, 3L);
        List<MeetingRules.Roll> ok = List.of(new MeetingRules.Roll(1L, "PRESENT", "GOOD"),
                new MeetingRules.Roll(2L, "PRESENT", "TIRED"), new MeetingRules.Roll(3L, "LEAVE", null));
        assertNull(MeetingRules.checkRoll(members, ok));
        assertTrue(MeetingRules.checkRoll(members, ok.subList(0, 2)).contains("1 名成员没点名"));
        assertNotNull(MeetingRules.checkRoll(members, List.of(new MeetingRules.Roll(1L, "PRESENT", "GOOD"), new MeetingRules.Roll(1L, "PRESENT", "GOOD"),
                new MeetingRules.Roll(3L, "LEAVE", null))));
        assertNotNull(MeetingRules.checkRoll(members, List.of(new MeetingRules.Roll(9L, "PRESENT", "GOOD"))));
        // 出席必须选状态
        assertNotNull(MeetingRules.checkRoll(Set.of(1L), List.of(new MeetingRules.Roll(1L, "PRESENT", null))));
        assertNotNull(MeetingRules.checkRoll(Set.of(1L), List.of(new MeetingRules.Roll(1L, "ALIEN", "GOOD"))));
        // 全部请假不能开
        assertNotNull(MeetingRules.checkRoll(Set.of(1L), List.of(new MeetingRules.Roll(1L, "LEAVE", null))));
        assertNotNull(MeetingRules.checkRoll(members, List.of()));
    }

    @Test
    void rate() {
        assertEquals(80, MeetingRules.rate(24, 30));
        assertEquals(100, MeetingRules.rate(31, 30));
        assertNull(MeetingRules.rate(0, 0));
        assertEquals(0, MeetingRules.rate(0, 30));
    }
}
