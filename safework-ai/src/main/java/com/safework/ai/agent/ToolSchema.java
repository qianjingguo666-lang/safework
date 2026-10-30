package com.safework.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;

/**
 * 生成工具参数的 JSON Schema，避免手写一长串 JSON：
 * <pre>
 * ToolSchema.object()
 *     .enumOf("status", "隐患状态", List.of("REPORTED", "RECTIFYING"))
 *     .integer("limit", "返回条数", 1, 10)
 *     .build();
 * </pre>
 */
public final class ToolSchema {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ObjectNode properties;
    private final ArrayNode required;

    private ToolSchema() {
        root.put("type", "object");
        properties = root.putObject("properties");
        required = root.putArray("required");
    }

    public static ToolSchema object() {
        return new ToolSchema();
    }

    public ToolSchema string(String name, String description) {
        properties.putObject(name).put("type", "string").put("description", description);
        return this;
    }

    public ToolSchema enumOf(String name, String description, Collection<String> values) {
        ObjectNode p = properties.putObject(name).put("type", "string").put("description", description);
        ArrayNode arr = p.putArray("enum");
        values.forEach(arr::add);
        return this;
    }

    public ToolSchema integer(String name, String description, int min, int max) {
        properties.putObject(name).put("type", "integer").put("description", description)
                .put("minimum", min).put("maximum", max);
        return this;
    }

    public ToolSchema bool(String name, String description) {
        properties.putObject(name).put("type", "boolean").put("description", description);
        return this;
    }

    public ToolSchema required(String... names) {
        for (String n : names) {
            required.add(n);
        }
        return this;
    }

    public ObjectNode build() {
        return root.deepCopy();
    }
}
