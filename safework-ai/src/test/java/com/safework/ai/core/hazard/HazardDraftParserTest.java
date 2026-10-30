package com.safework.ai.core.hazard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("解析大模型返回的隐患 JSON")
class HazardDraftParserTest {

    @Test
    @DisplayName("前后有说明文字和代码块，字段缺失为 null")
    void parse() {
        HazardDraft d = HazardDraftParser.parse("好的：\n```json\n{\"title\":\"阀门漏气\",\"category\":\"设备设施\",\"level\":\"MAJOR\",\"location\":\"罐区东侧\",\"spaceCode\":null}\n```");
        assertEquals("阀门漏气", d.title());
        assertEquals("MAJOR", d.level());
        assertNull(d.spaceCode());
        assertNull(d.description());
    }

    @Test
    @DisplayName("没有 JSON 或 JSON 不完整返回 null（由规则兜底）")
    void invalid() {
        assertNull(HazardDraftParser.parse("抱歉，我无法判断"));
        assertNull(HazardDraftParser.parse("{\"title\":\"阀门"));
    }
}
