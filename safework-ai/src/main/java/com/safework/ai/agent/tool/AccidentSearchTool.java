package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.accident.AccidentDTOs;
import com.safework.safety.accident.AccidentRules;
import com.safework.safety.accident.AccidentService;
import com.safework.safety.accident.AccidentVOs;
import com.safework.safety.entity.AccidentInvestigation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 查询事故调查：调查进度、“四不放过”、整改措施逾期 */
@Component
@RequiredArgsConstructor
public class AccidentSearchTool implements AgentTool {

    private static final Map<String, String> STATES = Map.of(
            AccidentRules.INVESTIGATING, "调查中", AccidentRules.OVERDUE, "调查超期", AccidentRules.CLOSED, "已结案");

    private final AccidentService accidentService;

    @Override
    public String name() {
        return "search_accidents";
    }

    @Override
    public String label() {
        return "查询事故调查";
    }

    @Override
    public String description() {
        return "查询事故调查处理情况：每起事故的调查组长、调查期限、状态（调查中 / 超期 / 已结案）、“四不放过”满足了几项、整改措施完成情况，"
                + "以及所有逾期未完成的事故整改措施（措施内容、责任人、期限）。"
                + "问“哪些事故整改逾期了”“硫化氢中毒那起事故调查到哪一步了”“今年结案了几起事故”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .enumOf("state", "调查状态：INVESTIGATING 调查中、OVERDUE 调查超期、CLOSED 已结案", List.of("INVESTIGATING", "OVERDUE", "CLOSED"))
                .string("keyword", "按事故名称、编号、调查组长模糊搜索")
                .integer("limit", "返回条数，默认 10", 1, 20)
                .build();
    }

    @Override
    public String permission() {
        return AccidentService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        AccidentDTOs.Query q = new AccidentDTOs.Query();
        q.setSize(ToolArgs.intIn(args, "limit", 10, 1, 20));
        q.setState(ToolArgs.oneOf(args, "state", "INVESTIGATING", "OVERDUE", "CLOSED"));
        q.setKeyword(ToolArgs.str(args, "keyword"));
        AccidentVOs.Page page = accidentService.page(q);
        List<Map<String, Object>> items = page.records().stream().map(r -> {
            AccidentInvestigation i = r.investigation();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventNo", i.getEventNo());
            m.put("title", i.getEventTitle());
            m.put("occurTime", ToolArgs.time(i.getOccurTime()));
            m.put("leader", i.getLeaderName());
            m.put("deadline", i.getDeadline().toString());
            m.put("state", STATES.get(i.getState()));
            m.put("fourNots", r.fourOk() + "/4 项满足");
            m.put("measures", r.measureDone() + "/" + r.measureTotal() + " 项完成" + (r.measureOverdue() > 0 ? "，" + r.measureOverdue() + " 项逾期" : ""));
            return m;
        }).toList();
        LocalDate today = LocalDate.now();
        Map<Long, String> titles = accidentService.openInvestigations().stream()
                .collect(Collectors.toMap(AccidentInvestigation::getId, AccidentInvestigation::getEventTitle, (a, b) -> a));
        List<Map<String, Object>> overdue = accidentService.openMeasures().stream()
                .filter(x -> today.isAfter(x.getDeadline()))
                .sorted((a, b) -> a.getDeadline().compareTo(b.getDeadline()))
                .limit(10)
                .map(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("accident", titles.get(x.getInvestigationId()));
                    m.put("measure", x.getContent());
                    m.put("owner", x.getOwnerName());
                    m.put("deadline", x.getDeadline().toString());
                    m.put("overdueDays", ChronoUnit.DAYS.between(x.getDeadline(), today));
                    return m;
                }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", page.total());
        data.put("items", items);
        data.put("overdueMeasures", overdue);
        return new ToolResult(data, "事故调查 " + page.total() + " 起，逾期整改措施 " + overdue.size() + " 项");
    }
}
