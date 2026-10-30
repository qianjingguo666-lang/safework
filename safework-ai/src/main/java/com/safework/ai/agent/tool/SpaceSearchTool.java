package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.dto.SpaceQueryDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询受限空间台账 */
@Component
@RequiredArgsConstructor
public class SpaceSearchTool implements AgentTool {

    private final SpaceService spaceService;

    @Override
    public String name() {
        return "search_spaces";
    }

    @Override
    public String label() {
        return "查询受限空间";
    }

    @Override
    public String description() {
        return "查询本企业的受限空间台账（储罐、反应釜、污水池、地下有限空间等），可以按风险等级、类型、关键词筛选，"
                + "返回总数 total 和最多 10 条明细，包括位置、所属部门、责任人、危险介质。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("riskLevel", "风险等级：HIGH 高，MEDIUM 中，LOW 低", List.of("HIGH", "MEDIUM", "LOW"))
                .string("spaceType", "空间类型，例如：储罐、反应釜、污水池、地下有限空间、管道")
                .string("keyword", "按编号、名称、位置模糊搜索")
                .integer("limit", "返回明细的条数，默认 5", 1, 10)
                .build();
    }

    @Override
    public String permission() {
        return "safety:space:list";
    }

    @Override
    public ToolResult execute(JsonNode args) {
        SpaceQueryDTO q = new SpaceQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 5, 1, 10));
        q.setRiskLevel(ToolArgs.oneOf(args, "riskLevel", "HIGH", "MEDIUM", "LOW"));
        q.setSpaceType(ToolArgs.str(args, "spaceType"));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        Page<ConfinedSpace> page = spaceService.page(q);

        List<Map<String, Object>> items = page.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("spaceCode", s.getSpaceCode());
            m.put("spaceName", s.getSpaceName());
            m.put("spaceType", s.getSpaceType());
            m.put("location", s.getLocation());
            m.put("dept", s.getDeptName());
            m.put("responsible", s.getResponsibleName());
            m.put("medium", s.getMedium());
            m.put("riskLevel", riskLabel(s.getRiskLevel()));
            m.put("status", Integer.valueOf(1).equals(s.getStatus()) ? "启用" : "停用");
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 个，返回 " + items.size() + " 个");
    }

    private static String riskLabel(String level) {
        if ("HIGH".equals(level)) {
            return "高";
        }
        return "LOW".equals(level) ? "低" : "中";
    }
}
