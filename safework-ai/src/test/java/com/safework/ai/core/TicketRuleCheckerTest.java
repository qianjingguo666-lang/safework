package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("作业票规则预审（AI 预审之前的确定性检查）")
class TicketRuleCheckerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);

    @Test
    @DisplayName("受限空间：缺少的安全措施、气体接近限值、检测过期、作业时间过长都能发现")
    void findsProblems() {
        List<TicketRuleChecker.Finding> f = TicketRuleChecker.check(new TicketRuleChecker.Input("CONFINED_SPACE",
                "1. 强制通风 2. 加盲板", 19.8, 6.0, null, NOW.minusMinutes(45), NOW, NOW.plusHours(10), NOW));
        assertEquals(3, f.stream().filter(x -> x.item().equals("安全措施")).count(), "缺少检测、防护、救援三类措施");
        assertTrue(f.stream().anyMatch(x -> x.problem().contains("接近限值")));
        assertTrue(f.stream().anyMatch(x -> x.problem().contains("偏高")));
        assertTrue(f.stream().anyMatch(x -> x.problem().contains("30 分钟")));
        assertTrue(f.stream().anyMatch(x -> x.level().equals("LOW")), "作业超过 8 小时给出低风险提示");
    }

    @Test
    @DisplayName("措施齐全、数据合格时没有问题")
    void cleanTicket() {
        List<TicketRuleChecker.Finding> f = TicketRuleChecker.check(new TicketRuleChecker.Input("CONFINED_SPACE",
                "强制通风；加盲板隔离；气体检测合格；佩戴正压式呼吸器；配备三脚架和安全绳", 20.9, 1.0, 0.2,
                NOW.minusMinutes(5), NOW, NOW.plusHours(4), NOW));
        assertTrue(f.isEmpty(), f.toString());
    }

    @Test
    @DisplayName("高处作业不要求气体检测；安全措施为空是高风险")
    void heightWork() {
        List<TicketRuleChecker.Finding> f = TicketRuleChecker.check(new TicketRuleChecker.Input("HEIGHT", "",
                null, null, null, null, NOW, NOW.plusHours(2), NOW));
        assertEquals(1, f.size());
        assertEquals("HIGH", f.get(0).level());
    }
}
