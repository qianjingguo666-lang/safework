package com.safework.ai.core.emergency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.core.JsonExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 事件 → 预案匹配、解析大模型的处置建议（纯计算，方便单元测试）。
 * <pre>
 * 1. 规则粗筛：事故类型一致 +50 分，标题、适用范围和事件描述的字词重合度最多 +50 分，取前 3 个交给大模型
 * 2. 大模型只能在这 3 个里选，处置要点只能来自预案原文
 * 3. 大模型的结果要校验：预案编号必须是候选之一，条数、长度有上限；没有大模型或解析失败就用规则结果兜底
 * </pre>
 */
public final class EmergencyMatcher {

    public static final int MAX_CANDIDATES = 3;
    public static final int MAX_STEPS = 8;
    public static final int MAX_CAUTIONS = 4;
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 预案正文里的步骤行：以“1.”“（1）”“一、”“第一步”开头 */
    private static final Pattern STEP_LINE = Pattern.compile("^\\s*(\\d+[.、．)]|[（(]\\d+[)）]|[一二三四五六七八九十]+、|第[一二三四五六七八九十]+步)\\s*");
    private static final Set<String> CAUTION_WORDS = Set.of("严禁", "禁止", "不得", "不准", "切勿");

    private EmergencyMatcher() {
    }

    /** 按分数从高到低取前 3 个（0 分的不要） */
    public static List<PlanCandidate> candidates(String accidentType, String text, List<PlanCandidate> plans) {
        record Scored(PlanCandidate p, int score) {
        }
        Set<String> eventGrams = bigrams(text);
        List<Scored> scored = new ArrayList<>();
        for (PlanCandidate p : plans) {
            int score = p.accidentType() != null && p.accidentType().equals(accidentType) ? 50 : 0;
            Set<String> planGrams = bigrams(p.title() + " " + nullToEmpty(p.scope()));
            if (!eventGrams.isEmpty() && !planGrams.isEmpty()) {
                long common = planGrams.stream().filter(eventGrams::contains).count();
                score += (int) Math.round(50.0 * Math.min(1.0, common / 6.0));
            }
            if (score > 0) {
                scored.add(new Scored(p, score));
            }
        }
        // 分数一样时，现场处置方案更具体，排在前面
        return scored.stream()
                .sorted(Comparator.comparingInt((Scored s) -> -s.score())
                        .thenComparing(s -> "ONSITE".equals(s.p().planType()) ? 0 : 1))
                .limit(MAX_CANDIDATES)
                .map(Scored::p)
                .toList();
    }

    /** 没有大模型时的兜底建议：第一个候选预案，处置要点取正文里的步骤行，注意事项取带“严禁、禁止”的句子 */
    public static EmergencyAdvice fallback(List<PlanCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new EmergencyAdvice(null, "没有找到匹配的预案，请手动选择或按综合应急预案处置", List.of(), List.of());
        }
        PlanCandidate p = candidates.get(0);
        return new EmergencyAdvice(p, "事故类型和适用范围最接近", stepsFrom(p.content()), cautionsFrom(p.content()));
    }

    static List<String> stepsFrom(String content) {
        List<String> steps = new ArrayList<>();
        if (content == null) {
            return steps;
        }
        for (String line : content.split("\\r?\\n")) {
            if (STEP_LINE.matcher(line).find()) {
                String s = STEP_LINE.matcher(line).replaceFirst("").trim();
                if (!s.isEmpty()) {
                    steps.add(truncate(s, 80));
                }
            }
            if (steps.size() >= MAX_STEPS) {
                break;
            }
        }
        return steps;
    }

    static List<String> cautionsFrom(String content) {
        List<String> result = new ArrayList<>();
        if (content == null) {
            return result;
        }
        for (String sentence : content.split("[。；;\\n]")) {
            String s = STEP_LINE.matcher(sentence).replaceFirst("").trim();
            if (CAUTION_WORDS.stream().anyMatch(s::contains) && !result.contains(s)) {
                result.add(truncate(s, 60));
            }
            if (result.size() >= MAX_CAUTIONS) {
                break;
            }
        }
        return result;
    }

    /**
     * 解析大模型的回答：{"planCode":"","reason":"","steps":[],"cautions":[]}。
     * 预案编号不是候选之一（模型编造）、或者没有步骤，返回 null 由调用方兜底
     */
    public static EmergencyAdvice parse(String reply, List<PlanCandidate> candidates) {
        String obj = JsonExtractor.firstObject(reply);
        if (obj == null) {
            return null;
        }
        try {
            JsonNode n = JSON.readTree(obj);
            String code = n.path("planCode").asText("").trim();
            PlanCandidate plan = candidates.stream().filter(c -> c.code().equalsIgnoreCase(code)).findFirst().orElse(null);
            if (plan == null) {
                return null;
            }
            List<String> steps = strings(n.path("steps"), MAX_STEPS, 80);
            if (steps.isEmpty()) {
                return null;
            }
            return new EmergencyAdvice(plan, truncate(n.path("reason").asText("").trim(), 100), steps,
                    strings(n.path("cautions"), MAX_CAUTIONS, 60));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> strings(JsonNode arr, int max, int len) {
        List<String> list = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode x : arr) {
                String s = STEP_LINE.matcher(x.asText("")).replaceFirst("").trim();
                if (!s.isEmpty() && list.size() < max) {
                    list.add(truncate(s, len));
                }
            }
        }
        return list;
    }

    /** 汉字、字母数字的两字组合，用来粗略计算文字重合度 */
    static Set<String> bigrams(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) {
            return set;
        }
        String t = text.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        for (int i = 0; i + 1 < t.length(); i++) {
            set.add(t.substring(i, i + 2));
        }
        return set;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
