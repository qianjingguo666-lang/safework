package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.kpi.KpiRules;
import com.safework.ticket.kpi.KpiService;
import com.safework.ticket.kpi.KpiVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询年度安全目标完成情况：总分、每个指标的实际值 / 目标 / 亮灯 */
@Component
@RequiredArgsConstructor
public class KpiTool implements AgentTool {

    private static final Map<String, String> LIGHTS = Map.of(KpiRules.GREEN, "达标", KpiRules.YELLOW, "接近目标",
            KpiRules.RED, "未达标", KpiRules.NO_DATA, "暂无数据");

    private final KpiService kpiService;

    @Override
    public String name() {
        return "get_safety_targets";
    }

    @Override
    public String label() {
        return "查询安全目标完成情况";
    }

    @Override
    public String description() {
        return "查询年度安全目标的完成情况（年初至今）：总分（百分制），以及每个指标的实际值、目标值、是否达标（达标 / 接近目标 / 未达标）、"
                + "分子分母和每月趋势。指标包括隐患按期整改率、隐患闭环率、安全检查按时完成率、班前会开展率、培训考试合格率、"
                + "安全责任书签署率、特种设备检验及时率、伤亡事故起数。问“今年安全目标完成得怎么样”“哪些指标没达标”“隐患整改率是多少”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().integer("year", "年度，默认今年", 2020, 2100).build();
    }

    @Override
    public String permission() {
        return KpiService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        int year = ToolArgs.intIn(args, "year", LocalDate.now().getYear(), 2020, LocalDate.now().getYear() + 1);
        KpiVOs.Report r = kpiService.report(year);
        List<Map<String, Object>> items = r.items().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metric", i.label());
            m.put("actual", i.actual() == null ? null : i.actual() + i.unit());
            m.put("target", (KpiRules.LOWER.equals(i.direction()) ? "不超过 " : "不低于 ") + i.target() + i.unit());
            m.put("result", LIGHTS.get(i.light()));
            if (i.denominator() != null) {
                m.put("detail", i.numerator() + "/" + i.denominator());
            }
            m.put("score", i.score() == null ? null : i.score() + "/" + i.weight());
            m.put("source", i.source());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", r.year());
        data.put("asOf", r.asOf());
        data.put("totalScore", r.totalScore());
        data.put("green", r.green());
        data.put("yellow", r.yellow());
        data.put("red", r.red());
        data.put("items", items);
        return new ToolResult(data, r.year() + " 年总分 " + r.totalScore() + "，未达标 " + r.red() + " 项");
    }
}
