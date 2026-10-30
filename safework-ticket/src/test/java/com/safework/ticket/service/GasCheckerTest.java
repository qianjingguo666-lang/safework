package com.safework.ticket.service;

import com.safework.common.exception.BizException;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("气体检测校验")
class GasCheckerTest {

    private static WorkTicket ticket(String o2, String lel, String h2s, int minutesAgo) {
        WorkTicket t = new WorkTicket();
        t.setTicketType(TicketType.CONFINED_SPACE);
        t.setGasOxygen(o2 == null ? null : new BigDecimal(o2));
        t.setGasLel(lel == null ? null : new BigDecimal(lel));
        t.setGasH2s(h2s == null ? null : new BigDecimal(h2s));
        t.setGasCheckTime(LocalDateTime.now().minusMinutes(minutesAgo));
        return t;
    }

    private static String error(WorkTicket t) {
        return assertThrows(BizException.class, () -> GasChecker.checkValues(t)).getMessage();
    }

    @Test
    @DisplayName("合格数据通过，边界值 19.5 / 23.5 / 10 也算合格")
    void valid() {
        assertDoesNotThrow(() -> GasChecker.checkValues(ticket("20.9", "2.5", "0.5", 5)));
        assertDoesNotThrow(() -> GasChecker.checkValues(ticket("19.5", "10", "10", 5)));
        assertDoesNotThrow(() -> GasChecker.checkValues(ticket("23.5", "0", null, 5)));
    }

    @Test
    @DisplayName("缺氧、富氧、可燃气体超标、硫化氢超标都拒绝")
    void invalid() {
        assertTrue(error(ticket("19.4", "1", "0", 5)).contains("氧含量"));
        assertTrue(error(ticket("23.6", "1", "0", 5)).contains("氧含量"));
        assertTrue(error(ticket("20.9", "10.1", "0", 5)).contains("可燃气体"));
        assertTrue(error(ticket("20.9", "1", "10.5", 5)).contains("硫化氢"));
    }

    @Test
    @DisplayName("必须填写检测数据")
    void missing() {
        assertTrue(error(ticket(null, "1", "0", 5)).contains("必须填写"));
    }

    @Test
    @DisplayName("开工时检测结果必须在 30 分钟内")
    void freshness() {
        assertDoesNotThrow(() -> GasChecker.checkFresh(ticket("20.9", "1", "0", 20)));
        String msg = assertThrows(BizException.class, () -> GasChecker.checkFresh(ticket("20.9", "1", "0", 31))).getMessage();
        assertTrue(msg.contains("30 分钟"));
    }
}
