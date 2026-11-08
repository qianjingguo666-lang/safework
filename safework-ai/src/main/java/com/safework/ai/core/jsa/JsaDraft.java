package com.safework.ai.core.jsa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.core.JsonExtractor;
import com.safework.ticket.jsa.JsaRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSA 起草：把作业类型、名称、内容、地点交给大模型，拆成作业步骤并识别危害、评估风险、写控制措施。
 * 大模型不可用时按作业类型的模板起草。起草结果只是草稿，编写人修改后提交审核。
 */
public final class JsaDraft {

    public static final int MAX_STEPS = 12;

    public static final String SYSTEM_PROMPT = """
            你是化工企业的安全工程师，要为一项作业编写“作业安全分析（JSA）”。
            要求：
            1. 按作业实际顺序拆成 4~10 个步骤（从作业前准备、能量隔离、检测、作业过程到收尾清理）；
            2. 每个步骤写：step（步骤，20 字以内）、hazards（这一步可能发生的危害和后果）、likelihood（可能性 1~5）、severity（严重度 1~5）、controls（控制措施）；
            3. 可能性 × 严重度 ≥ 15 的是重大风险，控制措施至少写 2 条，用中文分号“；”分开；
            4. 措施要具体可执行，符合 GB 30871《危险化学品企业特殊作业安全规范》，例如“进入前氧含量、可燃气、有毒气体三项检测合格”，不要写“注意安全”这种空话；
            5. 只根据给出的作业信息写，不要编造具体的设备位号或人名；
            6. ppe 写这项作业需要的劳动防护用品，用顿号分开。
            只输出 JSON，不要输出其他内容，格式：
            {"steps":[{"step":"…","hazards":"…","likelihood":3,"severity":4,"controls":"…；…"}],"ppe":"…"}""";

    /** 起草结果 */
    public record Draft(List<JsaRules.Step> steps, String ppe) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsaDraft() {
    }

    public static String userPrompt(String typeLabel, String title, String workContent, String location) {
        return "作业类型：" + (typeLabel == null ? "一般作业" : typeLabel) + "\n作业名称：" + cut(title, 60)
                + "\n作业地点：" + (location == null || location.isBlank() ? "未填写" : cut(location, 100))
                + "\n作业内容：" + cut(workContent, 500);
    }

    /** 解析大模型的回复：步骤至少 2 个；可能性、严重度不在 1~5 的改到边界，超长的截断；格式不对返回 null */
    public static Draft parse(String reply) {
        String obj = JsonExtractor.firstObject(reply);
        if (obj == null) {
            return null;
        }
        try {
            JsonNode root = JSON.readTree(obj);
            JsonNode arr = root.path("steps");
            List<JsaRules.Step> steps = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String step = n.path("step").asText("").trim();
                    String hazards = n.path("hazards").asText("").trim();
                    String controls = n.path("controls").asText("").trim();
                    if (step.isEmpty() || hazards.isEmpty() || controls.isEmpty() || steps.size() >= MAX_STEPS) {
                        continue;
                    }
                    steps.add(new JsaRules.Step(cut(step, 100), cut(hazards, 300), clamp(n.path("likelihood").asInt(3)),
                            clamp(n.path("severity").asInt(3)), cut(controls, 500)));
                }
            }
            if (steps.size() < JsaRules.MIN_STEPS) {
                return null;
            }
            String ppe = root.path("ppe").asText("").trim();
            return new Draft(steps, ppe.isEmpty() ? null : cut(ppe, 300));
        } catch (Exception e) {
            return null;
        }
    }

    /** 按作业类型的模板起草（大模型不可用时） */
    public static Draft fallback(String ticketType) {
        return TEMPLATES.getOrDefault(ticketType == null ? "" : ticketType, GENERAL);
    }

    private static final Draft CONFINED = new Draft(List.of(
            new JsaRules.Step("作业前准备、办理作业票", "作业人员不清楚风险，监护人不到位", 2, 4, "作业前进行安全交底；确认监护人持证在岗，全程不得离开"),
            new JsaRules.Step("能量隔离（切断物料、加盲板、断电）", "物料串入、设备意外启动导致中毒、机械伤害", 3, 5, "关闭阀门并加盲板隔离，挂牌上锁；切断搅拌等动力电源并验证"),
            new JsaRules.Step("清洗置换、强制通风", "残留有毒有害气体、缺氧", 3, 5, "蒸汽或氮气置换后用空气强制通风；通风时间不少于 30 分钟"),
            new JsaRules.Step("气体检测", "检测不准、检测时间过早导致误判", 3, 5, "进入前 30 分钟内检测氧含量、可燃气、有毒气体三项合格；检测点有代表性，上中下各测一次"),
            new JsaRules.Step("进入受限空间作业", "中毒窒息、淹溺、坠落", 3, 5, "作业中连续检测或每 2 小时复测；佩戴安全带和救生绳，出入口保持畅通；监护人与作业人员保持联络"),
            new JsaRules.Step("作业结束清点撤离", "人员、工具遗留在受限空间内", 2, 4, "清点人员和工具后封闭；监护人签字确认")), "安全帽、防护服、防毒面具或空气呼吸器、安全带、救生绳、防护手套");

    private static final Draft HOT = new Draft(List.of(
            new JsaRules.Step("作业前准备、办理动火票", "动火等级判断错误，审批不到位", 2, 4, "按动火部位确定动火等级，逐级审批；作业前安全交底"),
            new JsaRules.Step("隔离与清理动火点周围", "可燃物、可燃气体遇火花燃烧爆炸", 3, 5, "清除动火点周围 15 米内可燃物；封堵地漏、阴井；用防火毯遮盖"),
            new JsaRules.Step("动火分析", "可燃气体浓度超标", 3, 5, "动火前 30 分钟内可燃气体检测合格；作业中断超过 30 分钟重新检测"),
            new JsaRules.Step("动火作业（焊接、切割）", "火灾、灼烫、触电", 3, 4, "配备灭火器并专人监火；电焊机外壳接地，焊把线绝缘良好"),
            new JsaRules.Step("作业中监护", "监火人离开，火情不能及时发现", 2, 4, "监火人全程在场，发现异常立即停止作业"),
            new JsaRules.Step("作业结束清理现场", "残留火种引起火灾", 2, 4, "检查确认无残留火种后离开；动火后 30 分钟内复查")), "安全帽、焊接面罩、阻燃工作服、焊接手套、绝缘鞋");

    private static final Draft HEIGHT_W = new Draft(List.of(
            new JsaRules.Step("作业前准备、办理作业票", "作业人员身体不适、未经培训", 2, 4, "确认作业人员身体状况良好、持证上岗；安全交底"),
            new JsaRules.Step("搭设脚手架或检查作业平台", "脚手架不稳、护栏缺失导致坠落", 3, 5, "脚手架验收合格挂牌后使用；临边设置护栏和踢脚板"),
            new JsaRules.Step("攀爬和高处作业", "人员坠落", 3, 5, "安全带高挂低用，全程系挂；作业面下方设置安全网"),
            new JsaRules.Step("工具材料传递", "物体打击", 3, 4, "工具系防坠绳，材料用吊绳传递；下方拉警戒线禁止通过"),
            new JsaRules.Step("作业结束清理撤离", "遗留工具坠落", 2, 3, "清理作业面工具材料后撤离；拆除警戒前确认上方无遗留物")), "安全帽、安全带（双钩）、防滑鞋、防护手套");

    private static final Draft GENERAL = new Draft(List.of(
            new JsaRules.Step("作业前准备和安全交底", "作业人员不清楚风险", 2, 3, "作业前进行安全交底，明确作业范围和危险点"),
            new JsaRules.Step("能量隔离", "设备意外启动、介质泄漏", 3, 4, "切断电源和物料来源，挂牌上锁并验证"),
            new JsaRules.Step("实施作业", "机械伤害、物体打击", 2, 4, "按操作规程作业，使用合格工具；无关人员不得进入作业区"),
            new JsaRules.Step("收尾恢复", "现场遗留物、设备恢复不当", 2, 3, "清理现场，解除隔离前确认人员撤离、设备完好")), "安全帽、工作服、防护手套、安全鞋");

    private static final Map<String, Draft> TEMPLATES = Map.of("CONFINED_SPACE", CONFINED, "HOT_WORK", HOT, "HEIGHT", HEIGHT_W);

    private static int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    private static String cut(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n);
    }
}
