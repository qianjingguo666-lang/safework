package com.safework.ticket.print;

import com.safework.ticket.enums.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("扫码验真结论")
class TicketVerdictTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    @Test
    @DisplayName("已批准、作业中：计划时间内有效，过了计划结束时间不能作业")
    void approvedAndWorking() {
        assertEquals(TicketVerdict.OK, TicketVerdict.of(TicketStatus.APPROVED, NOW.plusHours(2), NOW).level());
        assertEquals(TicketVerdict.DANGER, TicketVerdict.of(TicketStatus.APPROVED, NOW.minusMinutes(1), NOW).level());
        assertEquals(TicketVerdict.OK, TicketVerdict.of(TicketStatus.IN_PROGRESS, NOW.plusHours(1), NOW).level());
        assertTrue(TicketVerdict.of(TicketStatus.IN_PROGRESS, NOW.minusHours(1), NOW).text().contains("超过"));
    }

    @Test
    @DisplayName("未批准、已作废不能作业；已完工提示不能再用")
    void others() {
        for (TicketStatus s : new TicketStatus[]{TicketStatus.DRAFT, TicketStatus.PENDING, TicketStatus.REJECTED, TicketStatus.CANCELLED}) {
            assertEquals(TicketVerdict.DANGER, TicketVerdict.of(s, NOW.plusHours(1), NOW).level());
        }
        assertEquals(TicketVerdict.WARN, TicketVerdict.of(TicketStatus.COMPLETED, NOW.minusHours(1), NOW).level());
    }
}
