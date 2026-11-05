package com.safework.ai.core.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.core.JsonExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 班前会安全要点：根据今天的作业票、重点风险点、未整改隐患生成。
 * 大模型不可用时按规则生成（每种作业的固定要点），保证班组长随时能用。
 */
public final class MeetingTips {

    public static final int MAX_TIPS = 8;
    public static final int MAX_TIP_LENGTH = 80;

    public static final String SYSTEM_PROMPT = """
            你是化工企业的班组安全员，要帮班组长准备今天班前会上讲的“安全要点”。
            根据【今天的作业】【重点风险】【未整改隐患】写 4~6 条，要求：
            1. 每条一句话，不超过 50 字，口语化，班组长能直接念；
            2. 先讲今天具体的作业和风险（写明作业类型和地点），再讲通用要求；
            3. 只能根据给出的信息写，不要编造没有的作业、地点或设备；
            4. 不要写“注意安全”这种空话，要写具体做法（例如“进罐前必须三项气体检测合格”）。
            只输出 JSON，不要输出其他内容，格式：{"tips":["…","…"]}""";

    /** 每种作业的固定要点（大模型不可用时用） */
    static final Map<String, String> WORK_TIPS = Map.of(
            "CONFINED_SPACE", "进入前先通风、氧含量/可燃气/硫化氢三项检测合格，监护人全程不得离开",
            "HOT_WORK", "动火点周围清除可燃物，可燃气体检测合格后才能动火，现场备好灭火器",
            "HEIGHT", "安全带高挂低用，工具系防坠绳，下方拉警戒线禁止人员通过");

    static final List<String> GENERAL = List.of(
            "上岗前检查劳动防护用品，按岗位要求正确佩戴",
            "发现泄漏、异味、异常声响立即报告，有权拒绝违章指挥和冒险作业");

    /** 今天的一张作业票 */
    public record Work(String type, String typeLabel, String title, String location) {
    }

    /** 一个重点风险点 */
    public record Risk(String name, String levelLabel, String measures) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private MeetingTips() {
    }

    public static String userPrompt(String team, List<Work> works, List<Risk> risks, int openHazards) {
        StringBuilder sb = new StringBuilder("班组：").append(team).append("\n\n【今天的作业】\n");
        if (works.isEmpty()) {
            sb.append("今天没有特殊作业票\n");
        }
        works.forEach(w -> sb.append("- ").append(w.typeLabel()).append("：").append(w.title()).append("，地点 ").append(w.location()).append('\n'));
        sb.append("\n【重点风险】\n");
        if (risks.isEmpty()) {
            sb.append("无\n");
        }
        risks.forEach(r -> sb.append("- ").append(r.name()).append("（").append(r.levelLabel()).append("）管控措施：")
                .append(cut(r.measures(), 120)).append('\n'));
        sb.append("\n【未整改隐患】").append(openHazards).append(" 条\n");
        return sb.toString();
    }

    /** 解析大模型的回复；格式不对返回 null */
    public static List<String> parse(String reply) {
        String obj = JsonExtractor.firstObject(reply);
        if (obj == null) {
            return null;
        }
        try {
            JsonNode arr = JSON.readTree(obj).path("tips");
            List<String> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String t = n.asText("").trim().replaceFirst("^\\d+[.、)）]\\s*", "");
                    if (!t.isEmpty() && list.size() < MAX_TIPS) {
                        list.add(cut(t, MAX_TIP_LENGTH));
                    }
                }
            }
            return list.size() >= 2 ? list : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 规则生成：每张作业票一条（同类作业的固定要点）+ 前两个重点风险 + 隐患提醒 + 通用要求 */
    public static List<String> fallback(List<Work> works, List<Risk> risks, int openHazards) {
        List<String> list = new ArrayList<>();
        for (Work w : works) {
            if (list.size() >= 4) {
                break;
            }
            list.add("今天" + w.location() + "有" + w.typeLabel() + "（" + w.title() + "）：" + WORK_TIPS.getOrDefault(w.type(), "严格按作业票上的安全措施执行"));
        }
        risks.stream().limit(2).forEach(r -> list.add("重点风险“" + r.name() + "”（" + r.levelLabel() + "）："
                + cut(r.measures() == null || r.measures().isBlank() ? "按管控措施执行" : r.measures(), 40)));
        if (openHazards > 0) {
            list.add("厂里还有 " + openHazards + " 条隐患没整改完，经过隐患区域注意警示标识");
        }
        list.addAll(GENERAL);
        return list.stream().limit(MAX_TIPS).map(t -> cut(t, MAX_TIP_LENGTH)).toList();
    }

    /** 拼成班组长能直接念、也能直接存的文字 */
    public static String join(List<String> tips) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tips.size(); i++) {
            sb.append(i + 1).append(". ").append(tips.get(i)).append('\n');
        }
        return sb.toString().trim();
    }

    private static String cut(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
