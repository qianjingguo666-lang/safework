package com.safework.ai.core.emergency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("应急预案匹配、处置建议解析")
class EmergencyMatcherTest {

    private static final String CONTENT = """
            一、发现有人晕倒在受限空间内，监护人立即呼救，并报告调度室。
            二、严禁未佩戴正压式空气呼吸器的人员进入施救。
            三、救援人员佩戴正压式空气呼吸器、系好安全绳后进入，将伤员救出。
            四、对伤员进行心肺复苏，拨打 120。""";
    private static final List<PlanCandidate> PLANS = List.of(
            new PlanCandidate(1L, "YA-ZH-01", "生产安全事故综合应急预案", "COMPREHENSIVE", "OTHER", "全厂", "1. 报告"),
            new PlanCandidate(2L, "YA-ZX-01", "受限空间中毒窒息现场处置方案", "ONSITE", "POISONING", "储罐、反应釜、污水池等受限空间作业", CONTENT),
            new PlanCandidate(3L, "YA-ZX-02", "储罐区泄漏火灾爆炸专项预案", "SPECIAL", "FIRE_EXPLOSION", "储罐区、装卸站", "1. 关阀"),
            new PlanCandidate(4L, "YA-ZX-03", "高处坠落现场处置方案", "ONSITE", "FALL", "屋顶、平台、脚手架", "1. 停止作业"));

    @Test
    @DisplayName("中毒窒息事件：同类型的现场处置方案排第一，不相关的不进候选")
    void candidates() {
        List<PlanCandidate> c = EmergencyMatcher.candidates("POISONING", "污水池清淤 有人晕倒在池内", PLANS);
        assertEquals("YA-ZX-01", c.get(0).code());
        assertTrue(c.size() <= EmergencyMatcher.MAX_CANDIDATES);
        assertTrue(c.stream().noneMatch(p -> p.code().equals("YA-ZX-03")));
    }

    @Test
    @DisplayName("兜底建议：步骤取正文里的编号行，注意事项取“严禁”的句子")
    void fallback() {
        EmergencyAdvice a = EmergencyMatcher.fallback(EmergencyMatcher.candidates("POISONING", "有人晕倒", PLANS));
        assertEquals("YA-ZX-01", a.plan().code());
        assertEquals(4, a.steps().size());
        assertTrue(a.steps().get(0).startsWith("发现有人晕倒"));
        assertTrue(a.cautions().get(0).startsWith("严禁"));
        assertNull(EmergencyMatcher.fallback(List.of()).plan());
    }

    @Test
    @DisplayName("解析大模型回答：编号必须是候选之一，去掉步骤前的序号")
    void parse() {
        List<PlanCandidate> c = EmergencyMatcher.candidates("POISONING", "污水池有人晕倒", PLANS);
        EmergencyAdvice a = EmergencyMatcher.parse("```json\n{\"planCode\":\"ya-zx-01\",\"reason\":\"受限空间中毒\",\"steps\":[\"1. 立即呼救\",\"佩戴呼吸器施救\"],\"cautions\":[\"严禁盲目施救\"]}\n```", c);
        assertEquals(2L, a.plan().id());
        assertEquals("立即呼救", a.steps().get(0));
        assertNull(EmergencyMatcher.parse("{\"planCode\":\"YA-999\",\"steps\":[\"x\"]}", c));
        assertNull(EmergencyMatcher.parse("{\"planCode\":\"YA-ZX-01\",\"steps\":[]}", c));
        assertNull(EmergencyMatcher.parse("无法判断", c));
    }
}
