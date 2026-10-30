package com.safework.ai.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.training.core.QuestionChecker;
import com.safework.training.core.QuestionDraft;
import com.safework.training.enums.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 解析大模型生成的题目 JSON。模型的输出格式并不总是听话，这里尽量兼容常见的“不规范”写法：
 * <ul>
 *   <li>JSON 前后带说明文字、包在 ```json 代码块里；</li>
 *   <li>题型写成中文（“单选题”）；</li>
 *   <li>选项带了字母前缀（“A. 19.5%”“B、18%”）；</li>
 *   <li>答案写成数组 ["A","C"]、“A、C”，或者直接写选项内容；判断题答案写成“正确 / 对 / true”。</li>
 * </ul>
 * 实在解析不了的字段留空，交给 {@link QuestionChecker} 判定为不合格，不会让错误的题进入题库。
 */
public final class AiQuestionParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** 选项前缀：A. / A、 / A． / (A) / （A） / A: / A） */
    private static final Pattern OPTION_PREFIX = Pattern.compile("^\\s*[(（]?[A-Fa-f][)）]?\\s*[.．、:：)）]\\s*|^\\s*[(（][A-Fa-f][)）]\\s*");
    /** 题干前的序号：1. / 1、 / （1） / 第1题 */
    private static final Pattern CONTENT_NO = Pattern.compile("^\\s*(第\\s*\\d+\\s*题[:：]?|[(（]\\d+[)）]|\\d+\\s*[.．、])\\s*");
    private static final Pattern LETTERS = Pattern.compile("^[A-Fa-f](\\s*[,，、/\\s]?\\s*[A-Fa-f])*$");

    private AiQuestionParser() {
    }

    public static List<QuestionDraft> parse(String reply) {
        JsonNode list = locateArray(reply);
        List<QuestionDraft> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (JsonNode n : list) {
            if (!n.isObject()) {
                continue;
            }
            QuestionType type = type(n.path("type").asText(""));
            String content = CONTENT_NO.matcher(n.path("content").asText(n.path("question").asText("")).trim()).replaceFirst("");
            List<String> options = new ArrayList<>();
            if (type == QuestionType.JUDGE) {
                options.addAll(QuestionChecker.JUDGE_OPTIONS);
            } else {
                for (JsonNode o : n.path("options")) {
                    options.add(OPTION_PREFIX.matcher(o.asText("")).replaceFirst("").trim());
                }
            }
            String answer = answer(type, n.path("answer"), options);
            String analysis = n.path("analysis").asText(n.path("explanation").asText("")).trim();
            result.add(new QuestionDraft(type, content, options, answer, analysis.isEmpty() ? null : analysis));
        }
        return result;
    }

    /** 找到题目数组：{"questions":[...]}、直接是数组、或者只有一道题的对象 */
    static JsonNode locateArray(String reply) {
        if (reply == null) {
            return null;
        }
        String obj = JsonExtractor.firstObject(reply);
        int arrStart = reply.indexOf('[');
        int objStart = reply.indexOf('{');
        try {
            if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
                int end = reply.lastIndexOf(']');
                if (end > arrStart) {
                    JsonNode arr = JSON.readTree(reply.substring(arrStart, end + 1));
                    if (arr.isArray()) {
                        return arr;
                    }
                }
            }
            if (obj != null) {
                JsonNode root = JSON.readTree(obj);
                if (root.path("questions").isArray()) {
                    return root.path("questions");
                }
                if (root.has("content") || root.has("question")) {
                    return JSON.createArrayNode().add(root);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static QuestionType type(String s) {
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (t.startsWith("SINGLE") || t.startsWith("单选")) {
            return QuestionType.SINGLE;
        }
        if (t.startsWith("MULTI") || t.startsWith("多选") || t.startsWith("MULTIPLE")) {
            return QuestionType.MULTI;
        }
        if (t.startsWith("JUDGE") || t.startsWith("判断") || t.startsWith("TRUE") || "TF".equals(t)) {
            return QuestionType.JUDGE;
        }
        return null;
    }

    static String answer(QuestionType type, JsonNode node, List<String> options) {
        String raw;
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            node.forEach(x -> sb.append(x.asText("")).append(','));
            raw = sb.toString();
        } else if (node.isBoolean()) {
            raw = node.asBoolean() ? "正确" : "错误";
        } else {
            raw = node.asText("");
        }
        raw = raw.trim();
        if (type == QuestionType.JUDGE) {
            String t = raw.toUpperCase(Locale.ROOT);
            if (t.equals("A") || t.equals("T") || t.equals("TRUE") || t.startsWith("正确") || t.equals("对") || t.equals("√") || t.equals("是")) {
                return "A";
            }
            if (t.equals("B") || t.equals("F") || t.equals("FALSE") || t.startsWith("错误") || t.equals("错") || t.equals("×") || t.equals("否")) {
                return "B";
            }
            return "";
        }
        String compact = raw.replaceAll("[,，、/\\s]+$", "");
        if (LETTERS.matcher(compact).matches()) {
            return QuestionChecker.normalizeAnswer(compact);
        }
        // 答案直接写了选项内容：按内容找回字母
        StringBuilder letters = new StringBuilder();
        for (String part : raw.split("[,，、;；]")) {
            String p = OPTION_PREFIX.matcher(part).replaceFirst("").trim();
            for (int i = 0; i < options.size(); i++) {
                if (!p.isEmpty() && options.get(i).equals(p)) {
                    letters.append(QuestionChecker.letter(i));
                }
            }
        }
        return QuestionChecker.normalizeAnswer(letters.toString());
    }
}
