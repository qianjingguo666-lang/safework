package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
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

    /**
     * 解析时间：支持 "2026-11-02 18:00"、"2026-11-02T18:00"、"2026-11-02"（只有日期时取当天 18:00 下班前）
     */
    public static LocalDateTime dateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim().replace('T', ' ').replace('/', '-');
        try {
            if (t.length() <= 10) {
                return LocalDate.parse(t).atTime(18, 0);
            }
            return LocalDateTime.parse(t.length() > 16 ? t.substring(0, 16) : t, TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式不对：" + s + "，请用 yyyy-MM-dd HH:mm 格式");
        }
    }

    /**
     * 按名字找唯一的一个：先精确匹配，再找包含这个名字的（“李四”能匹配“李四（作业负责人）”）。
     * 找不到或找到多个都抛异常，把候选人告诉模型，让它向用户确认，绝不随便选一个。
     */
    public static <T> T matchOne(List<T> items, Function<T, String> nameOf, String query, String what) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("请提供" + what + "的姓名");
        }
        String q = query.trim();
        List<T> exact = items.stream().filter(i -> q.equals(nameOf.apply(i))).toList();
        if (exact.size() == 1) {
            return exact.get(0);
        }
        List<T> fuzzy = exact.size() > 1 ? exact : items.stream().filter(i -> {
            String n = nameOf.apply(i);
            return n != null && n.contains(q);
        }).toList();
        if (fuzzy.isEmpty()) {
            throw new IllegalArgumentException("没有找到名为“" + q + "”的" + what);
        }
        if (fuzzy.size() > 1) {
            String names = String.join("、", fuzzy.stream().limit(10).map(nameOf).toList());
            throw new IllegalArgumentException("有多个" + what + "匹配“" + q + "”：" + names + "。请向用户确认是哪一位");
        }
        return fuzzy.get(0);
    }
}
