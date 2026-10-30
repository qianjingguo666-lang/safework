package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.dto.HazardQueryDTO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.service.HazardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询隐患：复用 HazardService.page，自动带数据权限（普通员工只能查到自己上报或负责整改的） */
@Component
@RequiredArgsConstructor
public class HazardSearchTool implements AgentTool {

    private final HazardService hazardService;

    @Override
    public String name() {
        return "search_hazards";
    }

    @Override
    public String label() {
        return "查询隐患";
    }

    @Override
    public String description() {
        return "查询本企业的安全隐患（只返回当前用户有权查看的）。可以按状态、等级、是否逾期、关键词筛选，"
                + "返回符合条件的总数 total 和最多 10 条明细。问“有多少条”时看 total，不需要把明细全部列出。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("status", "隐患状态：REPORTED 待指派，RECTIFYING 整改中，PENDING_VERIFY 待验收，CLOSED 已闭环，REVOKED 已撤销。不传表示全部",
                        Arrays.stream(HazardStatus.values()).map(Enum::name).toList())
                .enumOf("level", "隐患等级：GENERAL 一般隐患，MAJOR 重大隐患", List.of("GENERAL", "MAJOR"))
                .bool("overdue", "true 表示只查整改已超过期限的隐患")
                .string("keyword", "按隐患编号、标题、位置模糊搜索")
                .integer("limit", "返回明细的条数，默认 5", 1, 10)
                .build();
    }

    @Override
    public String permission() {
        return "safety:hazard:list";
    }

    @Override
    public ToolResult execute(JsonNode args) {
        HazardQueryDTO q = new HazardQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 5, 1, 10));
        q.setStatus(ToolArgs.enumValue(args, "status", HazardStatus.class));
        q.setLevel(ToolArgs.oneOf(args, "level", "GENERAL", "MAJOR"));
        q.setOverdue(ToolArgs.bool(args, "overdue"));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        Page<Hazard> page = hazardService.page(q);

        List<Map<String, Object>> items = page.getRecords().stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hazardNo", h.getHazardNo());
            m.put("title", h.getTitle());
            m.put("level", "MAJOR".equals(h.getLevel()) ? "重大隐患" : "一般隐患");
            m.put("status", h.getStatus() == null ? null : h.getStatus().getLabel());
            m.put("category", h.getCategory());
            m.put("location", h.getLocation());
            m.put("reporter", h.getReporterName());
            m.put("rectifier", h.getRectifierName());
            m.put("deadline", ToolArgs.time(h.getDeadline()));
            m.put("overdue", Boolean.TRUE.equals(h.getOverdue()));
            m.put("reportTime", ToolArgs.time(h.getCreateTime()));
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 条，返回 " + items.size() + " 条");
    }
}
