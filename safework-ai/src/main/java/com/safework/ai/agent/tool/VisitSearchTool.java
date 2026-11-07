package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.entity.VisitPass;
import com.safework.ticket.visit.VisitRules;
import com.safework.ticket.visit.VisitService;
import com.safework.ticket.visit.VisitVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询外来人员和车辆：现在在厂的（应急清点）、今天预约的、危化品车辆 */
@Component
@RequiredArgsConstructor
public class VisitSearchTool implements AgentTool {

    private final VisitService visitService;

    @Override
    public String name() {
        return "search_visitors";
    }

    @Override
    public String label() {
        return "查询外来人员和车辆";
    }

    @Override
    public String description() {
        return "查询外来人员和车辆入厂情况：现在在厂的访客、车辆、危化品运输车辆（姓名、单位、车牌、接待人、入厂时间，发生事故时清点外来人员用），"
                + "今天预约要来的，待审批的危化品车辆，超时未离厂的。问“现在厂里有几个外来人员”“今天有哪些危化品车辆进厂”“谁还没离厂”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return VisitService.PERM_GATE;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        LocalDate today = LocalDate.now();
        VisitVOs.Overview o = visitService.overview();
        List<VisitVOs.Row> gate = visitService.gate();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onsitePeople", o.onsitePeople());
        data.put("onsiteHazmatVehicles", o.onsiteHazmat());
        data.put("pendingApproval", o.pending());
        data.put("onsite", gate.stream().filter(r -> VisitRules.ENTERED.equals(r.pass().getStatus())).map(r -> brief(r, today)).toList());
        data.put("expectedToday", gate.stream().filter(r -> !VisitRules.ENTERED.equals(r.pass().getStatus())).map(r -> brief(r, today)).toList());
        return new ToolResult(data, "在厂 " + o.onsitePeople() + " 人，危化品车辆 " + o.onsiteHazmat() + " 辆");
    }

    private static Map<String, Object> brief(VisitVOs.Row r, LocalDate today) {
        VisitPass p = r.pass();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", VisitRules.TYPES.get(p.getVisitType()));
        m.put("name", p.getVisitorName());
        m.put("company", p.getCompany());
        m.put("people", p.getPeopleCount());
        if (p.getPlateNo() != null) {
            m.put("plate", p.getPlateNo());
        }
        if (p.getCargoName() != null) {
            m.put("cargo", p.getCargoName() + " " + p.getCargoQty() + " 吨");
        }
        m.put("host", p.getHostName());
        m.put("status", VisitRules.STATUS.get(p.getStatus()));
        if (p.getEnterTime() != null) {
            m.put("enterTime", p.getEnterTime());
        }
        if (r.overstay()) {
            m.put("overstay", "预约日期 " + p.getPlanDate() + "，超时未离厂");
        }
        if (!r.problems().isEmpty()) {
            m.put("cannotEnter", r.problems());
        }
        return m;
    }
}
