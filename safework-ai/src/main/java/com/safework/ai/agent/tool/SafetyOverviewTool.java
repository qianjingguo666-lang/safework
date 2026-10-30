package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.dashboard.DashboardService;
import com.safework.ticket.dashboard.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 安全总体情况：复用数据看板的统计 */
@Component
@RequiredArgsConstructor
public class SafetyOverviewTool implements AgentTool {

    private final DashboardService dashboardService;

    @Override
    public String name() {
        return "get_safety_overview";
    }

    @Override
    public String label() {
        return "查询安全总览";
    }

    @Override
    public String description() {
        return "查询本企业的安全生产总体统计（和数据看板一致）：最近 N 天新增作业票数量及类型分布、当前作业中和待审批的作业票数、"
                + "未闭环隐患数、逾期隐患数、重大隐患数、整改按期完成率、最近 N 天新增和闭环的隐患数、隐患类别分布、受限空间风险分布。"
                + "适合回答“最近安全情况怎么样”“本月作业票多少”“隐患整改率”这类整体性问题。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object()
                .integer("days", "统计最近多少天，默认 30（本月、近一个月都用 30；本周用 7）", 7, 90)
                .build();
    }

    @Override
    public String permission() {
        return "dashboard:view";
    }

    @Override
    public ToolResult execute(JsonNode args) {
        int days = ToolArgs.intIn(args, "days", 30, 7, 90);
        DashboardVO vo = dashboardService.overview(days);
        DashboardVO.Kpi k = vo.getKpi();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", days);
        data.put("newTicketsInPeriod", k.getTicketTotal());
        Map<String, Long> byType = new LinkedHashMap<>();
        vo.getTicketTrend().forEach((type, series) -> byType.put(type, sum(series)));
        data.put("newTicketsByType", byType);
        data.put("ticketStatusInPeriod", toMap(vo.getTicketStatus()));
        data.put("ticketsInProgressNow", k.getTicketInProgress());
        data.put("ticketsPendingApprovalNow", k.getTicketPending());
        data.put("openHazardsNow", k.getHazardOpen());
        data.put("overdueHazardsNow", k.getHazardOverdue());
        data.put("majorOpenHazardsNow", k.getHazardMajorOpen());
        data.put("rectifyOnTimeRatePercent", k.getRectifyOnTimeRate());
        data.put("hazardsReportedInPeriod", sum(vo.getHazardReported()));
        data.put("hazardsClosedInPeriod", sum(vo.getHazardClosed()));
        Map<String, Object> category = new LinkedHashMap<>();
        for (DashboardVO.CategoryStat c : vo.getHazardCategory()) {
            category.put(c.name(), Map.of("open", c.open(), "closed", c.closed()));
        }
        data.put("hazardCategory", category);
        data.put("spaceTotal", k.getSpaceTotal());
        data.put("spaceHighRisk", k.getSpaceHighRisk());
        data.put("spaceWorkingNow", k.getSpaceWorking());
        data.put("spaceRisk", toMap(vo.getSpaceRisk()));
        return new ToolResult(data, "最近 " + days + " 天：新增作业票 " + k.getTicketTotal() + " 张，未闭环隐患 " + k.getHazardOpen() + " 条");
    }

    private static long sum(List<Long> list) {
        return list == null ? 0 : list.stream().mapToLong(Long::longValue).sum();
    }

    private static Map<String, Long> toMap(List<DashboardVO.NameValue> list) {
        Map<String, Long> m = new LinkedHashMap<>();
        list.forEach(nv -> m.put(nv.name(), nv.value()));
        return m;
    }
}
