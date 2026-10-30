package com.safework.ai.core.hazard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.core.JsonExtractor;

/** 解析大模型返回的隐患 JSON（模型常在 JSON 前后加说明文字，字段也可能缺失） */
public final class HazardDraftParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HazardDraftParser() {
    }

    /** 解析失败返回 null，由调用方用关键词规则兜底 */
    public static HazardDraft parse(String reply) {
        String obj = JsonExtractor.firstObject(reply);
        if (obj == null) {
            return null;
        }
        try {
            JsonNode n = JSON.readTree(obj);
            return new HazardDraft(text(n, "title"), text(n, "category"), text(n, "level"), text(n, "location"),
                    text(n, "spaceCode"), text(n, "description"), text(n, "levelReason"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
