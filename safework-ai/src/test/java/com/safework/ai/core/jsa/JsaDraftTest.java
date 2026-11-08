package com.safework.ai.core.jsa;

import com.safework.ticket.jsa.JsaRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsaDraftTest {

    @Test
    void parse() {
        JsaDraft.Draft d = JsaDraft.parse("好的：```json\n{\"steps\":[{\"step\":\"通风\",\"hazards\":\"缺氧\",\"likelihood\":9,\"severity\":0,\"controls\":\"强制通风\"},"
                + "{\"step\":\"检测\",\"hazards\":\"中毒\",\"likelihood\":3,\"severity\":5,\"controls\":\"三项检测合格；连续监测\"},"
                + "{\"step\":\"\",\"hazards\":\"x\",\"controls\":\"y\"}],\"ppe\":\"安全帽、空气呼吸器\"}\n```");
        assertNotNull(d);
        assertEquals(2, d.steps().size(), "空步骤跳过");
        assertEquals(5, d.steps().get(0).likelihood(), "9 改到 5");
        assertEquals(1, d.steps().get(0).severity(), "0 改到 1");
        assertEquals("安全帽、空气呼吸器", d.ppe());
        assertNull(JsaDraft.parse("{\"steps\":[{\"step\":\"a\",\"hazards\":\"b\",\"controls\":\"c\"}]}"), "只有 1 步");
        assertNull(JsaDraft.parse("注意安全"));
        assertNull(JsaDraft.parse("{\"steps\":\"不是数组\"}"));
    }

    @Test
    void fallbackTemplatesPassRules() {
        for (String t : new String[]{"CONFINED_SPACE", "HOT_WORK", "HEIGHT", null, "OTHER"}) {
            JsaDraft.Draft d = JsaDraft.fallback(t);
            assertNull(JsaRules.checkSteps(d.steps()), "模板要能直接通过校验：" + t);
            assertNotNull(d.ppe());
        }
        assertTrue(JsaDraft.fallback("CONFINED_SPACE").steps().size() >= 5);
    }

    @Test
    void prompt() {
        String p = JsaDraft.userPrompt("受限空间作业", "调节池清淤", "清理池底污泥", null);
        assertTrue(p.contains("受限空间作业") && p.contains("调节池清淤") && p.contains("未填写"));
    }
}
