package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.drill.DrillRules;
import com.safework.safety.drill.DrillService;
import com.safework.safety.drill.DrillVOs;
import com.safework.safety.emergency.EmergencyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询应急演练：年度演练计划完成情况、即将进行的、待评估的、最近的评估得分、没转整改的问题 */
@Component
@RequiredArgsConstructor
public class DrillTool implements AgentTool {

    private final DrillService drillService;

    @Override
    public String name() {
        return "get_emergency_drills";
    }

    @Override
    public String label() {
        return "查询应急演练";
    }

    @Override
    public String description() {
        return "查询应急演练情况：今年每份应急预案的演练完成情况（综合、专项预案每年至少 1 次，现场处置方案每年至少 2 次；DONE 已达标 / PLANNED 已安排 / MISSING 还没安排），"
                + "近 30 天要进行的演练，已演练还没评估的，最近几次的评估得分和等级（百分制，优秀 / 良好 / 合格 / 不合格），预案是否需要修订，发现的问题还有几条没转整改。"
                + "问“今年演练计划完成了吗”“哪些预案还没演练”“最近一次演练评得怎么样”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return DrillService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        LocalDate today = LocalDate.now();
        DrillVOs.Overview o = drillService.overview(today);
        DrillVOs.Annual a = drillService.annual(today.getYear());
        List<Map<String, Object>> plans = a.rows().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("plan", r.planCode() + " " + r.planTitle() + "（" + EmergencyRules.PLAN_TYPES.get(r.planType()) + "）");
            m.put("required", r.required());
            m.put("done", r.done());
            m.put("planned", r.planned());
            m.put("state", r.state());
            if (r.reviseNote() != null) {
                m.put("needRevise", r.reviseNote());
            }
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", a.year());
        data.put("plans", plans);
        data.put("plansMissing", a.missing());
        data.put("upcoming", o.upcoming().stream().map(d -> d.getPlanTime().toLocalDate() + " " + d.getTitle() + "（" + ("LIVE".equals(d.getDrillType()) ? "实战" : "桌面推演") + "）").toList());
        data.put("pendingEvaluate", o.pendingEvaluate().stream().map(d -> d.getActualTime().toLocalDate() + " " + d.getTitle()).toList());
        data.put("recordLate", o.recordLate().stream().map(d -> d.getPlanTime().toLocalDate() + " " + d.getTitle()).toList());
        data.put("recentEvaluated", o.recentEvaluated().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("drill", d.getActualTime().toLocalDate() + " " + d.getTitle());
            m.put("score", d.getTotalScore());
            m.put("grade", d.getGrade());
            m.put("planSuitable", Integer.valueOf(1).equals(d.getPlanSuitable()));
            return m;
        }).toList());
        data.put("problemsNotRectified", o.openProblems());
        return new ToolResult(data, "今年演练 " + o.yearDone() + " 次，" + a.missing() + " 份预案还没安排够");
    }
}
