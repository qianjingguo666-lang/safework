package com.safework.ticket.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("看板：按天补齐数据")
class DashboardServiceTest {

    @Test
    @DisplayName("没有数据的日期补 0，顺序和日期轴一致")
    void fillMissingDays() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        List<LocalDate> days = List.of(d1, d1.plusDays(1), d1.plusDays(2));
        assertEquals(List.of(0L, 5L, 0L), DashboardService.fill(days, Map.of(d1.plusDays(1), 5L)));
    }
}
