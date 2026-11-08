package com.safework.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.commit.CommitRules;
import com.safework.ticket.commit.CommitService;
import com.safework.ticket.commit.CommitVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询安全风险研判与承诺：今天研判到哪一步、异常项和管控措施、系统统计、本月按时承诺率 */
@Component
@RequiredArgsConstructor
public class CommitTool implements AgentTool {

    private final CommitService commitService;

    @Override
    public String name() {
        return "get_risk_commitment";
    }

    @Override
    public String label() {
        return "查询安全风险研判承诺";
    }

    @Override
    public String description() {
        return "查询今天的安全风险研判与承诺公告：生产、设备、工艺、安全负责人谁研判了、还差谁（9:00 前完成），研判出的异常项和管控措施，"
                + "主要负责人是否已签字承诺（10:00 前），生产装置运行情况，今天的动火 / 受限空间 / 高处作业票数，重大危险源、超储、未整改隐患数；"
                + "以及本月按时承诺率。问“今天承诺了吗”“今天研判有什么异常”“这个月承诺按时吗”时使用。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return CommitService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        CommitVOs.Today t = commitService.today();
        CommitVOs.History h = commitService.history(java.time.YearMonth.from(t.date()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", t.date());
        data.put("committed", t.commitment() != null && CommitRules.COMMITTED.equals(t.commitment().getStatus()));
        if (t.commitment() != null && t.commitment().getCommitTime() != null) {
            data.put("committer", t.commitment().getCommitterName() + " " + t.commitment().getCommitTime().toLocalTime().withNano(0));
        }
        data.put("judged", t.aspects().stream().filter(a -> a.judgerName() != null).map(a -> a.label() + "：" + a.judgerName()).toList());
        data.put("missing", t.missing().stream().map(CommitRules.ASPECTS::get).toList());
        data.put("judgeLate", t.judgeLate());
        data.put("commitLate", t.commitLate());
        List<Map<String, Object>> abnormal = t.aspects().stream().flatMap(a -> a.items().stream().filter(i -> Boolean.FALSE.equals(i.ok())).map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("aspect", a.label());
            m.put("item", i.text());
            m.put("situation", i.note());
            m.put("measure", i.measure());
            return m;
        })).toList();
        data.put("abnormal", abnormal);
        if (t.commitment() != null && t.commitment().getUnitsTotal() != null) {
            var c = t.commitment();
            data.put("units", "装置 " + c.getUnitsTotal() + " 套：运行 " + c.getUnitsRunning() + "、停产 " + c.getUnitsStopped() + "、检修 " + c.getUnitsOverhaul());
        }
        data.put("stats", t.stats());
        data.put("monthRate", h.rate() == null ? null : h.rate() + "%（按时 " + h.onTimeDays() + " / " + h.days() + " 天）");
        return new ToolResult(data, (Boolean.TRUE.equals(data.get("committed")) ? "今天已承诺" : "今天还没承诺") + "，异常 " + abnormal.size() + " 项");
    }
}
