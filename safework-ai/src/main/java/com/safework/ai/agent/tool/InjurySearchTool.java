package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.injury.InjuryDTOs;
import com.safework.safety.injury.InjuryRules;
import com.safework.safety.injury.InjuryService;
import com.safework.safety.injury.InjuryVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询工伤：今年工伤人数、待申请认定（倒计时）、处理中的案件、费用 */
@Component
@RequiredArgsConstructor
public class InjurySearchTool implements AgentTool {

    private final InjuryService injuryService;

    @Override
    public String name() {
        return "search_injuries";
    }

    @Override
    public String label() {
        return "查询工伤";
    }

    @Override
    public String description() {
        return "查询工伤管理情况：今年工伤人数，还没申请工伤认定的（受伤 30 日内必须申请，返回剩余天数或超期天数），处理中的案件（状态、伤残等级、停工留薪期），"
                + "今年工伤费用（工伤保险基金支付 / 单位支付，单位：元）。问“有没有工伤认定快到期的”“今年有几起工伤”“工伤花了多少钱”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return InjuryService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        InjuryVOs.Overview o = injuryService.overview();
        InjuryDTOs.Query q = new InjuryDTOs.Query();
        q.setSize(30);
        List<Map<String, Object>> cases = injuryService.page(q).records().stream()
                .filter(r -> !InjuryRules.CLOSED.equals(r.injury().getStatus())).map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("caseNo", r.injury().getCaseNo());
                    m.put("injured", r.injury().getInjuredName() + "（" + r.injury().getEmployerName() + "）");
                    m.put("injuryDate", r.injury().getInjuryTime().toLocalDate());
                    m.put("injury", r.injury().getInjuryNature() == null ? r.injury().getDescription() : r.injury().getInjuryPart() + " " + r.injury().getInjuryNature());
                    m.put("status", InjuryRules.STATUS.get(r.injury().getStatus()));
                    if (InjuryRules.REPORTED.equals(r.injury().getStatus())) {
                        m.put("applyDeadline", r.deadline().dueDate() + (r.deadline().daysLeft() < 0 ? "（已超期 " + (-r.deadline().daysLeft()) + " 天）" : "（还剩 " + r.deadline().daysLeft() + " 天）"));
                    }
                    if (r.injury().getGrade() != null) {
                        m.put("grade", InjuryRules.gradeLabel(r.injury().getGrade()));
                    }
                    if (r.injury().getLeaveEnd() != null) {
                        m.put("paidLeave", r.injury().getLeaveStart() + " ~ " + r.injury().getLeaveEnd());
                    }
                    m.put("costTotal", r.costTotal());
                    return m;
                }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("yearCases", o.yearCases());
        data.put("pendingApply", o.pendingApply());
        data.put("overdueApply", o.overdueApply());
        data.put("openCases", cases);
        data.put("yearCostFund", o.yearFund());
        data.put("yearCostCompany", o.yearCompany());
        return new ToolResult(data, "今年工伤 " + o.yearCases() + " 人，待申请认定 " + o.pendingApply() + " 人");
    }
}
