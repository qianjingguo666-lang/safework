package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 读取大模型传来的工具参数。
 * 大模型给的参数不一定可靠（类型不对、枚举值写错、超出范围），这里统一做校验和纠正，
 * 无法纠正的抛 IllegalArgumentException，错误信息会返回给大模型让它重试。
 */
public final class ToolArgs {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ToolArgs() {
    }

    /** 字符串参数，空字符串当作没传 */
    public static String str(JsonNode args, String name) {
        JsonNode n = args.path(name);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** 整数参数：没传用默认值；超出范围自动收到范围内（比如要 100 条，只给 10 条） */
    public static int intIn(JsonNode args, String name, int def, int min, int max) {
        JsonNode n = args.path(name);
        int v;
        if (n.isNumber()) {
            v = n.asInt();
        } else if (n.isTextual() && n.asText().trim().matches("-?\\d{1,9}")) {
            v = Integer.parseInt(n.asText().trim());
        } else {
            v = def;
        }
        return Math.max(min, Math.min(max, v));
    }

    /** 布尔参数：兼容模型传 "true" 字符串 */
    public static Boolean bool(JsonNode args, String name) {
        JsonNode n = args.path(name);
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isTextual()) {
            String s = n.asText().trim();
            if ("true".equalsIgnoreCase(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return null;
    }

    /** 枚举参数：不区分大小写；取值不对时把可选值告诉模型 */
    public static <E extends Enum<E>> E enumValue(JsonNode args, String name, Class<E> type) {
        String s = str(args, name);
        if (s == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(s)) {
                return e;
            }
        }
        throw new IllegalArgumentException("参数 " + name + " 的取值 " + s + " 无效，可选值：" + names(type));
    }

    /** 字符串枚举（数据库里存的是字符串的字段，例如隐患等级） */
    public static String oneOf(JsonNode args, String name, String... values) {
        String s = str(args, name);
        if (s == null) {
            return null;
        }
        for (String v : values) {
            if (v.equalsIgnoreCase(s)) {
                return v;
            }
        }
        throw new IllegalArgumentException("参数 " + name + " 的取值 " + s + " 无效，可选值：" + String.join("、", values));
    }

    public static String time(LocalDateTime t) {
        return t == null ? null : t.format(TIME);
    }

    private static <E extends Enum<E>> String names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining("、"));
    }
}
