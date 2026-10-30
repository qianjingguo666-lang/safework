package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("工具参数校验和 Schema 生成")
class ToolArgsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    enum Color { RED, GREEN }

    private static JsonNode args(String json) throws Exception {
        return JSON.readTree(json);
    }

    @Test
    @DisplayName("整数：超出范围自动收回，字符串数字也能识别，没传用默认值")
    void integers() throws Exception {
        assertEquals(10, ToolArgs.intIn(args("{\"limit\":100}"), "limit", 5, 1, 10));
        assertEquals(1, ToolArgs.intIn(args("{\"limit\":-3}"), "limit", 5, 1, 10));
        assertEquals(7, ToolArgs.intIn(args("{\"limit\":\"7\"}"), "limit", 5, 1, 10));
        assertEquals(5, ToolArgs.intIn(args("{}"), "limit", 5, 1, 10));
        assertEquals(5, ToolArgs.intIn(args("{\"limit\":\"很多\"}"), "limit", 5, 1, 10));
    }

    @Test
    @DisplayName("枚举：不区分大小写；取值错误时提示可选值")
    void enums() throws Exception {
        assertEquals(Color.RED, ToolArgs.enumValue(args("{\"c\":\"red\"}"), "c", Color.class));
        assertNull(ToolArgs.enumValue(args("{\"c\":\"\"}"), "c", Color.class));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ToolArgs.enumValue(args("{\"c\":\"BLUE\"}"), "c", Color.class));
        assertTrue(e.getMessage().contains("RED、GREEN"));
        assertEquals("HIGH", ToolArgs.oneOf(args("{\"r\":\"high\"}"), "r", "HIGH", "LOW"));
    }

    @Test
    @DisplayName("布尔：兼容字符串 true / false")
    void booleans() throws Exception {
        assertEquals(Boolean.TRUE, ToolArgs.bool(args("{\"b\":\"true\"}"), "b"));
        assertEquals(Boolean.FALSE, ToolArgs.bool(args("{\"b\":false}"), "b"));
        assertNull(ToolArgs.bool(args("{}"), "b"));
    }

    @Test
    @DisplayName("Schema：生成 OpenAI 要求的 JSON Schema 格式")
    void schema() {
        ObjectNode s = ToolSchema.object()
                .enumOf("status", "状态", List.of("A", "B"))
                .integer("limit", "条数", 1, 10)
                .required("status")
                .build();
        assertEquals("object", s.path("type").asText());
        assertEquals("B", s.path("properties").path("status").path("enum").get(1).asText());
        assertEquals(10, s.path("properties").path("limit").path("maximum").asInt());
        assertEquals("status", s.path("required").get(0).asText());
    }

    @Test
    @DisplayName("时间：只有日期时取当天 18:00；兼容 T 和斜杠；格式错误给出提示")
    void dateTime() {
        assertEquals(LocalDateTime.of(2026, 11, 2, 18, 0), ToolArgs.dateTime("2026-11-02"));
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 30), ToolArgs.dateTime("2026-11-02T09:30"));
        assertEquals(LocalDateTime.of(2026, 11, 2, 9, 30), ToolArgs.dateTime("2026/11/02 09:30:15"));
        assertNull(ToolArgs.dateTime(" "));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ToolArgs.dateTime("下周三")).getMessage().contains("yyyy-MM-dd"));
    }

    @Test
    @DisplayName("按姓名找人：精确优先、模糊包含；找不到或有多个都报错并列出候选人，不随便选")
    void matchOne() {
        List<String> users = List.of("李四（作业负责人）", "李四光", "张三（安全员）", "赵六");
        Function<String, String> self = x -> x;
        assertEquals("赵六", ToolArgs.matchOne(users, self, "赵六", "员工"));
        assertEquals("张三（安全员）", ToolArgs.matchOne(users, self, "张三", "员工"));
        IllegalArgumentException many = assertThrows(IllegalArgumentException.class, () -> ToolArgs.matchOne(users, self, "李四", "员工"));
        assertTrue(many.getMessage().contains("李四（作业负责人）") && many.getMessage().contains("李四光"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ToolArgs.matchOne(users, self, "王五", "员工")).getMessage().contains("没有找到"));
    }
}
