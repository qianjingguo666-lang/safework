package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolArgs;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.safety.cost.CostRules;
import com.safework.safety.cost.CostService;
import com.safework.safety.cost.CostVOs;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询安全生产费用：应提、结转、已用、余额、每类预算和使用、提醒 */
@Component
@RequiredArgsConstructor
public class CostTool implements AgentTool {

    private static final Map<String, String> STATES = Map.of(CostRules.NORMAL, "正常", CostRules.OVER, "超预算", CostRules.UNPLANNED, "无预算支出");

    private final CostService costService;

    @Override
    public String name() {
        return "get_safety_cost";
    }

    @Override
    public String label() {
        return "查询安全生产费用";
    }

    @Override
    public String description() {
        return "查询年度安全生产费用（安全投入）的提取和使用情况：上年营业收入、本年应提（超额累退计算）、上年结转、当年可用、已使用、余额、使用率，"
                + "每个使用类别（安全防护设施、应急救援、风险管控与隐患整改、检查评价、劳保用品、培训、新技术、检测检验、安全责任险、其他）的预算、已用、是否超预算，"
                + "用于隐患整改的金额，以及需要关注的提醒。金额单位：元。问“今年安全费用用了多少”“哪类超预算了”“安全投入够不够”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().integer("year", "年度，默认今年", 2020, 2100).build();
    }

    @Override
    public String permission() {
        return CostService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        int year = ToolArgs.intIn(args, "year", LocalDate.now().getYear(), 2020, LocalDate.now().getYear() + 1);
        CostVOs.Report r = costService.report(year);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("year", r.year());
        data.put("asOf", r.asOf());
        data.put("hasPlan", r.hasPlan());
        if (r.hasPlan()) {
            data.put("lastYearRevenue", r.plan().getRevenue().stripTrailingZeros().toPlainString() + " 万元");
            data.put("accrue", r.plan().getAccrueAmount());
            data.put("carryOver", r.plan().getCarryOver());
            data.put("available", r.available());
        }
        data.put("used", r.used());
        data.put("balance", r.balance());
        data.put("usageRate", r.usageRate() == null ? null : r.usageRate() + "%");
        data.put("timeProgress", r.timeProgress() + "%");
        data.put("expenseCount", r.expenseCount());
        data.put("hazardRectifyUsed", r.hazardUsed());
        List<Map<String, Object>> cats = r.categories().stream().filter(c -> c.budget().signum() > 0 || c.used().signum() > 0).map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", c.label());
            m.put("budget", c.budget());
            m.put("used", c.used());
            m.put("count", c.count());
            m.put("state", STATES.get(c.state()));
            return m;
        }).toList();
        data.put("categories", cats);
        data.put("alerts", r.alerts());
        return new ToolResult(data, r.year() + " 年已使用 " + CostRules.yuan(r.used()) + " 元" + (r.usageRate() == null ? "" : "（" + r.usageRate() + "%）")
                + "，提醒 " + r.alerts().size() + " 条");
    }
}
