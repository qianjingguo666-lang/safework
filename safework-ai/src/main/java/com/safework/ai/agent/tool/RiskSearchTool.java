package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.dto.RiskQueryDTO;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.service.RiskRules;
import com.safework.safety.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询风险点：风险等级、管控措施、巡检是否逾期 */
@Component
@RequiredArgsConstructor
public class RiskSearchTool implements AgentTool {

    private final RiskService riskService;

    @Override
    public String name() {
        return "search_risk_points";
    }

    @Override
    public String label() {
        return "查询风险点";
    }

    @Override
    public String description() {
        return "查询本企业的风险分级管控清单：风险点的等级（LEC 评估）、危险有害因素、管控措施、责任人、巡检周期和是否逾期。"
                + "问“有哪些重大风险”“哪些风险点没按时巡检”“某个设备有什么风险、怎么管控”时使用。结果按风险从高到低排列。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("riskLevel", "风险等级：MAJOR 重大（红）、HIGH 较大（橙）、GENERAL 一般（黄）、LOW 低（蓝）",
                        Arrays.stream(RiskLevel.values()).map(Enum::name).toList())
                .bool("overdue", "true 表示只查巡检已逾期的")
                .string("keyword", "按编号、名称、位置模糊搜索")
                .integer("limit", "返回明细的条数，默认 5", 1, 10)
                .build();
    }

    @Override
    public String permission() {
        return RiskService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        RiskQueryDTO q = new RiskQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 5, 1, 10));
        q.setRiskLevel(ToolArgs.enumValue(args, "riskLevel", RiskLevel.class));
        q.setOverdue(ToolArgs.bool(args, "overdue"));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        q.setStatus(1);
        Page<RiskPoint> page = riskService.page(q);
        List<Map<String, Object>> items = page.getRecords().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getPointCode());
            m.put("name", p.getPointName());
            m.put("type", p.getPointType());
            m.put("location", p.getLocation());
            m.put("riskLevel", p.getRiskLevel().getLabel() + "（" + p.getRiskLevel().getColor() + "）");
            m.put("lec", "L=" + p.getLecL() + " E=" + p.getLecE() + " C=" + p.getLecC() + " D=" + p.getLecD());
            m.put("hazardFactors", p.getHazardFactors());
            m.put("possibleAccidents", p.getPossibleAccidents());
            m.put("controlMeasures", RiskRules.measures(p.getControlMeasures()));
            m.put("responsible", p.getResponsibleName());
            m.put("checkCycleDays", p.getCheckCycleDays());
            m.put("nextCheckTime", ToolArgs.time(p.getNextCheckTime()));
            m.put("checkOverdue", RiskRules.OVERDUE.equals(p.getCheckState()));
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 个，返回 " + items.size() + " 个");
    }
}
