package com.safework.safety.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("风险巡检规则")
class RiskRulesTest {

    @Test
    @DisplayName("管控措施按行拆分，去掉序号和空行")
    void measures() {
        assertEquals(List.of("进出物料管线加盲板", "作业前气体检测", "配备正压式空气呼吸器", "设专人监护"),
                RiskRules.measures("1. 进出物料管线加盲板\n2、作业前气体检测\r\n\n（3）配备正压式空气呼吸器\n- 设专人监护\n  "));
        assertEquals(List.of(), RiskRules.measures(null));
        // 数值开头的措施不能被当成序号去掉
        assertEquals(List.of("10% LEL 以上报警"), RiskRules.measures("10% LEL 以上报警"));
    }

    @Test
    @DisplayName("巡检状态：已过时间算逾期，今天之内算今天到期")
    void state() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 30, 10, 0);
        assertEquals(RiskRules.OVERDUE, RiskRules.state(now.minusMinutes(1), now));
        assertEquals(RiskRules.OVERDUE, RiskRules.state(null, now));
        assertEquals(RiskRules.TODAY, RiskRules.state(now.withHour(18), now));
        assertEquals(RiskRules.UPCOMING, RiskRules.state(now.plusDays(1), now));
        assertEquals(-2, RiskRules.daysLeft(now.minusDays(2), LocalDate.of(2026, 10, 30)));
        assertEquals(7, RiskRules.daysLeft(now.plusDays(7), LocalDate.of(2026, 10, 30)));
    }
}
