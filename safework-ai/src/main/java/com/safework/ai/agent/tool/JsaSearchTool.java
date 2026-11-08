package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.jsa.JsaRules;
import com.safework.ticket.jsa.JsaService;
import com.safework.ticket.jsa.JsaVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检索已发布的作业安全分析（JSA）：某类作业分几步、每步有什么危害、风险多大、怎么控制 */
@Component
@RequiredArgsConstructor
public class JsaSearchTool implements AgentTool {

    private final JsaService jsaService;

    @Override
    public String name() {
        return "search_jsa";
    }

    @Override
    public String label() {
        return "检索作业安全分析";
    }

    @Override
    public String description() {
        return "检索本企业已审核发布的作业安全分析（JSA）：作业分哪几步、每一步的危害、风险等级（可能性 × 严重度）、控制措施和需要的劳动防护用品。"
                + "问“调节池清淤有什么危害”“动火作业要采取哪些措施”“高处作业 JSA”时使用。回答时说明出自哪份 JSA（编号）。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .string("keyword", "作业名称或关键词，例如“清淤”“焊接”“屋顶”；不传表示不按关键词筛选")
                .enumOf("ticketType", "作业类型：CONFINED_SPACE 受限空间作业，HOT_WORK 动火作业，HEIGHT 高处作业",
                        Arrays.stream(TicketType.values()).map(Enum::name).toList())
                .build();
    }

    @Override
    public String permission() {
        return JsaService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        String keyword = ToolArgs.str(args, "keyword");
        TicketType type = ToolArgs.enumValue(args, "ticketType", TicketType.class);
        List<JsaVOs.Option> found = jsaService.search(keyword, type == null ? null : type.name(), 3);
        List<Map<String, Object>> items = found.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jsaNo", o.jsaNo());
            m.put("title", o.title());
            m.put("maxRisk", JsaRules.LEVELS.get(o.maxLevel()));
            m.put("ppe", o.ppe());
            m.put("steps", o.steps().stream().map(s -> s.seq() + ". " + s.step() + "｜危害：" + s.hazards() + "｜" + JsaRules.LEVELS.get(s.level())
                    + "（" + s.risk() + "）｜措施：" + s.controls()).toList());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", items.size());
        data.put("items", items);
        return new ToolResult(data, "找到 " + items.size() + " 份已发布的 JSA");
    }
}
