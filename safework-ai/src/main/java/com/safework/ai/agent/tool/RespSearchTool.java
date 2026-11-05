package com.safework.ai.agent.tool;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;
import com.safework.ai.agent.ToolSchema;
import com.safework.ticket.resp.RespRules;
import com.safework.ticket.resp.RespService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 查询安全生产责任书：我的签署情况；管理员还能看每份责任书的签署和考核进度 */
@Component
@RequiredArgsConstructor
public class RespSearchTool implements AgentTool {

    private final RespService respService;

    @Override
    public String name() {
        return "search_responsibility";
    }

    @Override
    public String label() {
        return "查询安全生产责任书";
    }

    @Override
    public String description() {
        return "查询安全生产责任制：当前用户自己的年度责任书（签了没有、截止日期、是否逾期签署、考核了没有）；"
                + "如果当前用户是安全管理员，还返回每份年度责任书的签署进度和考核进度。"
                + "问“我的责任书签了没有”“今年的责任书还有多少人没签”时使用。签署要到【安全管理 → 安全生产责任制】或手机上手写签名。";
    }

    @Override
    public ObjectNode parameters() {
        return ToolSchema.object().build();
    }

    @Override
    public String permission() {
        return RespService.PERM_LIST;
    }

    @Override
    public ToolResult execute(JsonNode args) {
        List<Map<String, Object>> mine = respService.mine().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year", b.book().getYear());
            m.put("title", b.book().getTitle());
            m.put("deadline", b.book().getDeadline().toString());
            m.put("bookStatus", RespRules.BOOK_STATUS.get(b.book().getStatus()));
            m.put("signed", b.signed() == 1);
            m.put("lateSigned", b.late() == 1);
            m.put("assessed", b.assessed() == 1);
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mine", mine);
        if (StpUtil.hasPermission(RespService.PERM_EDIT)) {
            data.put("allBooks", respService.books().stream().map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", b.book().getTitle());
                m.put("status", RespRules.BOOK_STATUS.get(b.book().getStatus()));
                m.put("deadline", b.book().getDeadline().toString());
                m.put("signed", b.signed() + "/" + b.total());
                m.put("lateSigned", b.late());
                m.put("assessed", b.assessed() + "/" + b.total());
                return m;
            }).toList());
        }
        long pending = mine.stream().filter(m -> !(Boolean) m.get("signed") && "签署中".equals(m.get("bookStatus"))).count();
        return new ToolResult(data, "我的责任书 " + mine.size() + " 份，待签 " + pending + " 份");
    }
}
