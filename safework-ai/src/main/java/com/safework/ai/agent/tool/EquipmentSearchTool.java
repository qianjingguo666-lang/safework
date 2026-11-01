package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.entity.Equipment;
import com.safework.safety.equipment.EquipmentQueryDTO;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.equipment.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询设备设施：哪些设备检验逾期、快到期 */
@Component
@RequiredArgsConstructor
public class EquipmentSearchTool implements AgentTool {

    private static final Map<String, String> STATE_LABELS = Map.of(
            EquipmentRules.OVERDUE, "检验逾期", EquipmentRules.DUE_SOON, "30 天内到期", EquipmentRules.NEVER, "从未检验",
            EquipmentRules.OK, "正常", EquipmentRules.NOT_IN_USE, "停用 / 报废");

    private final EquipmentService equipmentService;

    @Override
    public String name() {
        return "search_equipment";
    }

    @Override
    public String label() {
        return "查询设备设施";
    }

    @Override
    public String description() {
        return "查询本企业的设备设施台账：特种设备（压力容器、起重机械等）、安全附件（安全阀、压力表）、气体报警器、消防设施，"
                + "以及定期检验情况（上次检验、下次检验、是否逾期、最近一次检验结论）。"
                + "问“哪些设备检验过期了”“下个月有哪些安全阀要校验”“R-201 什么时候检验”时使用。结果按下次检验日期从近到远排列。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("inspectState", "检验状态：OVERDUE 逾期、DUE_SOON 30 天内到期、NEVER 从未检验、OK 正常（都只查在用的）",
                        List.of(EquipmentRules.OVERDUE, EquipmentRules.DUE_SOON, EquipmentRules.NEVER, EquipmentRules.OK))
                .enumOf("category", "大类：" + EquipmentRules.CATEGORIES, List.copyOf(EquipmentRules.CATEGORIES.keySet()))
                .string("keyword", "按位号、名称、位置模糊搜索")
                .integer("limit", "返回明细的条数，默认 10", 1, 20)
                .build();
    }

    @Override
    public String permission() {
        return EquipmentService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        EquipmentQueryDTO q = new EquipmentQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 10, 1, 20));
        q.setInspectState(ToolArgs.oneOf(args, "inspectState", EquipmentRules.OVERDUE, EquipmentRules.DUE_SOON, EquipmentRules.NEVER, EquipmentRules.OK));
        q.setCategory(ToolArgs.oneOf(args, "category", EquipmentRules.CATEGORIES.keySet().toArray(String[]::new)));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        Page<Equipment> page = equipmentService.page(q);
        List<Map<String, Object>> items = page.getRecords().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", e.getEquipCode());
            m.put("name", e.getName());
            m.put("type", EquipmentRules.TYPES.get(e.getEquipType()).label());
            m.put("location", e.getLocation());
            m.put("responsible", e.getResponsibleName());
            m.put("status", EquipmentRules.STATUS_LABELS.get(e.getStatus()));
            m.put("cycleMonths", e.getCycleMonths());
            m.put("lastInspectDate", e.getLastInspectDate() == null ? null : e.getLastInspectDate().toString());
            m.put("lastResult", e.getLastResult() == null ? null : (EquipmentRules.PASS.equals(e.getLastResult()) ? "合格" : "不合格"));
            m.put("nextInspectDate", e.getNextInspectDate() == null ? null : e.getNextInspectDate().toString());
            m.put("inspectState", STATE_LABELS.get(e.getInspectState()));
            m.put("daysLeft", e.getDaysLeft());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 台，返回 " + items.size() + " 台");
    }
}
