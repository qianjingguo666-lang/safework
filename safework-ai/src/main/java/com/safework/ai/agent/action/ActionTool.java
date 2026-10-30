package com.safework.ai.agent.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.AgentTool;
import com.safework.ai.agent.ToolResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把写操作包装成大模型能调用的工具：调用结果只是“已生成确认卡片”，
 * 明确告诉模型操作还没有执行，避免它对用户说“已经办好了”。
 */
public class ActionTool implements AgentTool {

    private final AgentAction action;
    private final PendingActionService pending;

    public ActionTool(AgentAction action, PendingActionService pending) {
        this.action = action;
        this.pending = pending;
    }

    @Override
    public String name() {
        return action.name();
    }

    @Override
    public String label() {
        return action.label();
    }

    @Override
    public String description() {
        return action.description() + "【写操作：调用后不会立即执行，只会给用户生成一张确认卡片，用户点击确认后才执行】";
    }

    @Override
    public ObjectNode parameters() {
        return action.parameters();
    }

    @Override
    public String permission() {
        return action.permission();
    }

    @Override
    public ToolResult execute(JsonNode args) {
        ActionCard card = pending.propose(action, args);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "WAITING_USER_CONFIRMATION");
        data.put("message", "已生成确认卡片，操作还没有执行。请告诉用户核对卡片内容后点击【确认执行】，不要说已经完成。");
        Map<String, String> preview = new LinkedHashMap<>();
        card.fields().forEach(f -> preview.put(f.label(), f.value()));
        data.put("preview", preview);
        return new ToolResult(data, "已生成确认卡片，等待用户确认", card);
    }
}
