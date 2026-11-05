package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.entity.MocChange;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.moc.MocDTOs;
import com.safework.safety.moc.MocRules;
import com.safework.safety.moc.MocService;
import com.safework.safety.moc.MocVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询变更管理：要我处理的变更、审批进度、临时变更恢复 */
@Component
@RequiredArgsConstructor
public class MocSearchTool implements AgentTool {

    private final MocService mocService;

    @Override
    public String name() {
        return "search_changes";
    }

    @Override
    public String label() {
        return "查询变更管理";
    }

    @Override
    public String description() {
        return "查询变更管理（MOC）：工艺、设备、原辅料、关键人员、管理制度的变更申请，包括状态（风险评估 / 待审批 / 实施中 / 投用前检查 / 已投用）、"
                + "风险等级（LEC 的 D 值）、审批进度（几人已批 / 需要几人）、临时变更的恢复日期和是否超期未恢复。"
                + "todo=true 只查“要当前用户处理的”（我要评估、我要审批、我要实施或恢复的）。"
                + "问“我有哪些变更要审批”“T-103 改储乙醇批了没有”“哪些临时变更过期还没恢复”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .bool("todo", "只看要我处理的")
                .enumOf("status", "状态", List.copyOf(MocRules.STATUSES.keySet()))
                .string("keyword", "按名称、编号、申请人、地点模糊搜索")
                .integer("limit", "返回条数，默认 10", 1, 20)
                .build();
    }

    @Override
    public String permission() {
        return MocService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        MocDTOs.Query q = new MocDTOs.Query();
        q.setSize(ToolArgs.intIn(args, "limit", 10, 1, 20));
        q.setTodo(ToolArgs.bool(args, "todo"));
        q.setStatus(ToolArgs.oneOf(args, "status", MocRules.STATUSES.keySet().toArray(String[]::new)));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        MocVOs.Page page = mocService.page(q);
        List<Map<String, Object>> items = page.records().stream().map(r -> {
            MocChange c = r.change();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("changeNo", c.getChangeNo());
            m.put("title", c.getTitle());
            m.put("type", MocRules.TYPES.get(c.getChangeType()));
            m.put("applicant", c.getApplicantName());
            m.put("status", MocRules.STATUSES.get(c.getStatus()));
            m.put("risk", c.getRiskLevel() == null ? "未评估" : RiskLevel.valueOf(c.getRiskLevel()).getLabel() + "（D = " + c.getLecD() + "）");
            if (MocRules.APPROVING.equals(c.getStatus())) {
                m.put("approval", r.approved() + "/" + c.getApprovalsNeeded() + " 人已批");
            }
            if (Integer.valueOf(1).equals(c.getTemporary())) {
                m.put("temporary", "临时变更，" + c.getRestoreDate() + " 恢复" + (Integer.valueOf(1).equals(c.getRestored()) ? "（已恢复）" : r.restoreOverdue() ? "（已超期未恢复）" : ""));
            }
            m.put("needMyAction", r.todo());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.total());
        data.put("items", items);
        data.put("note", "审批、评估、投用前检查要到【安全管理 → 变更管理】页面办理");
        return new ToolResult(data, "共 " + page.total() + " 项变更");
    }
}
