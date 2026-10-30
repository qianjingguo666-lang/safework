package com.safework.ai.agent.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.dto.TicketQueryDTO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询作业票：复用 TicketService.page，自动带数据权限（申请人、监护人只能查到和自己有关的） */
@Component
@RequiredArgsConstructor
public class TicketSearchTool implements AgentTool {

    private final TicketService ticketService;

    @Override
    public String name() {
        return "search_tickets";
    }

    @Override
    public String label() {
        return "查询作业票";
    }

    @Override
    public String description() {
        return "查询本企业的特殊作业票（受限空间、动火、高处作业，只返回当前用户有权查看的）。可以按状态、作业类型、关键词筛选，"
                + "返回总数 total 和最多 10 条明细，包括计划时间、申请人、监护人。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("status", "作业票状态：DRAFT 草稿，PENDING 待审批，APPROVED 已批准，IN_PROGRESS 作业中，COMPLETED 已完成，REJECTED 已驳回，CANCELLED 已取消。不传表示全部",
                        Arrays.stream(TicketStatus.values()).map(Enum::name).toList())
                .enumOf("ticketType", "作业类型：CONFINED_SPACE 受限空间作业，HOT_WORK 动火作业，HEIGHT 高处作业",
                        Arrays.stream(TicketType.values()).map(Enum::name).toList())
                .string("keyword", "按作业票编号、标题、地点模糊搜索")
                .integer("limit", "返回明细的条数，默认 5", 1, 10)
                .build();
    }

    @Override
    public String permission() {
        return "ticket:list";
    }

    @Override
    public ToolResult execute(JsonNode args) {
        TicketQueryDTO q = new TicketQueryDTO();
        q.setSize(ToolArgs.intIn(args, "limit", 5, 1, 10));
        q.setStatus(ToolArgs.enumValue(args, "status", TicketStatus.class));
        q.setTicketType(ToolArgs.enumValue(args, "ticketType", TicketType.class));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        Page<WorkTicket> page = ticketService.page(q);

        List<Map<String, Object>> items = page.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ticketNo", t.getTicketNo());
            m.put("title", t.getTitle());
            m.put("type", t.getTicketType() == null ? null : t.getTicketType().getLabel());
            m.put("status", t.getStatus() == null ? null : t.getStatus().getLabel());
            m.put("location", t.getLocation());
            m.put("space", t.getSpaceName());
            m.put("applicant", t.getApplicantName());
            m.put("guardian", t.getGuardianName());
            m.put("approver", t.getApproverName());
            m.put("planStart", ToolArgs.time(t.getPlanStartTime()));
            m.put("planEnd", ToolArgs.time(t.getPlanEndTime()));
            m.put("actualStart", ToolArgs.time(t.getActualStartTime()));
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.getTotal());
        data.put("items", items);
        return new ToolResult(data, "共 " + page.getTotal() + " 张，返回 " + items.size() + " 张");
    }
}
