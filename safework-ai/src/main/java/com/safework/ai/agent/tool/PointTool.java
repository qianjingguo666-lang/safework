package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.point.PointRules;
import com.safework.safety.point.PointService;
import com.safework.safety.point.PointVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 查询隐患随手拍积分排行榜和概况 */
@Component
@RequiredArgsConstructor
public class PointTool implements AgentTool {

    private final PointService pointService;

    @Override
    public String name() {
        return "get_point_ranking";
    }

    @Override
    public String label() {
        return "查询隐患随手拍积分";
    }

    @Override
    public String description() {
        return "查询隐患随手拍积分：本月或今年的积分排行榜（名次、员工、部门、得分、被确认的隐患条数），以及今年发出的积分、参与人数、"
                + "兑换奖品待发放和已发放金额。积分规则：上报的隐患被确认，一般隐患 10 分、重大隐患 50 分；按期完成整改 5 分。"
                + "问“这个月谁上报隐患最多”“积分排行榜”“随手拍开展得怎么样”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().enumOf("period", "MONTH 本月（默认）/ YEAR 今年", java.util.List.of("MONTH", "YEAR")).build();
    }

    @Override
    public String permission() {
        return PointService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        String period = args != null && args.hasNonNull("period") && PointRules.YEAR.equalsIgnoreCase(args.get("period").asText()) ? PointRules.YEAR : PointRules.MONTH;
        PointVOs.Ranking r = pointService.ranking(period, null, null);
        PointVOs.Overview o = pointService.overview(LocalDate.now().getYear());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", r.label());
        data.put("ranking", r.rows().stream().limit(10).map(x -> Map.of("rank", x.rank(), "name", x.userName(),
                "dept", x.deptName() == null ? "" : x.deptName(), "points", x.points(), "confirmedHazards", x.hazards())).toList());
        data.put("periodPoints", r.totalPoints());
        data.put("periodConfirmedHazards", r.totalHazards());
        Map<String, Object> year = new LinkedHashMap<>();
        year.put("points", o.yearPoints());
        year.put("confirmedHazards", o.yearHazards());
        year.put("reporters", o.reporters());
        year.put("redeemPending", o.pending());
        year.put("redeemIssued", o.issued());
        year.put("issuedAmountYuan", o.issuedAmount());
        data.put("thisYear", year);
        return new ToolResult(data, r.label() + "积分榜 " + r.rows().size() + " 人");
    }
}
