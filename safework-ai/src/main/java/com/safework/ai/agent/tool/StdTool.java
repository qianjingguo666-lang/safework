package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.entity.StdAssessment;
import com.safework.ticket.entity.StdItem;
import com.safework.ticket.std.StdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询安全生产标准化自评：最近一次完成的得分、等级、扣分项，以及正在进行的自评 */
@Component
@RequiredArgsConstructor
public class StdTool implements AgentTool {

    private final StdService stdService;

    @Override
    public String name() {
        return "get_standardization";
    }

    @Override
    public String label() {
        return "查询标准化自评";
    }

    @Override
    public String description() {
        return "查询安全生产标准化自评情况：最近一次完成的自评（日期、得分、等级、扣分项和扣分说明、是否已转隐患整改），"
                + "以及正在进行中的自评。问“标准化自评多少分”“标准化是几级”“自评扣分主要在哪些方面”“扣分项整改了没有”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return StdService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        Map<String, Object> data = new LinkedHashMap<>();
        StdAssessment last = stdService.latestDone();
        if (last == null) {
            data.put("latest", null);
        } else {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("title", last.getTitle());
            l.put("finishDate", last.getFinishDate());
            l.put("score", last.getScore());
            l.put("grade", last.getGrade());
            l.put("leader", last.getLeaderName());
            l.put("summary", last.getSummary());
            List<StdItem> items = stdService.deductedItems(last.getId());
            l.put("deductions", items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("item", i.getCode() + " " + i.getTitle());
                m.put("element", i.getElementName());
                m.put("deduct", i.getDeduct() + "/" + i.getStandardScore());
                m.put("note", i.getNote());
                m.put("rectifyHazard", i.getHazardNo());
                return m;
            }).toList());
            data.put("latest", l);
        }
        StdAssessment draft = stdService.currentDraft();
        if (draft != null) {
            data.put("inProgress", Map.of("title", draft.getTitle(), "startDate", draft.getStartDate(), "leader", draft.getLeaderName()));
        }
        return new ToolResult(data, last == null ? "还没有完成过标准化自评" : last.getTitle() + "：" + last.getScore() + " 分，" + last.getGrade());
    }
}
