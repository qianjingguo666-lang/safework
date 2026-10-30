package com.safework.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.dto.ChatMessage;
import com.safework.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 智能体的核心循环（ReAct / Function Calling）：
 * <pre>
 *   ① 把问题和可用工具发给大模型
 *   ② 大模型返回 tool_calls → 逐个执行工具 → 把结果作为 role=tool 的消息追加到对话 → 回到 ①
 *   ③ 大模型返回普通回答（没有 tool_calls）→ 结束
 * </pre>
 * 防护：
 * <ul>
 *   <li>最多 maxRounds 轮，防止模型反复调用工具停不下来（也是成本上限）；到上限后不再提供工具，要求直接回答；</li>
 *   <li>每轮最多执行 {@link #MAX_CALLS_PER_ROUND} 个工具；同样的工具和参数只执行一次，重复调用直接返回上次的结果；</li>
 *   <li>工具不存在、没有权限、参数错误、执行异常，都把错误作为工具结果告诉模型，而不是让整个问答失败；</li>
 *   <li>工具结果过长时截断，控制 token 消耗。</li>
 * </ul>
 * 这个类不依赖 Spring 和登录信息（权限判断通过 permission 传进来），方便单元测试。
 */
public class AgentRunner {

    static final int MAX_CALLS_PER_ROUND = 4;
    static final String LIMIT_PROMPT = "工具调用次数已达到上限。请不要再调用工具，根据上面已经查到的信息直接回答；信息不足的部分如实说明。";

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    private final AgentModel model;
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final Predicate<String> permission;
    private final int maxRounds;
    private final int maxResultChars;
    private final ObjectMapper json = new ObjectMapper();

    public AgentRunner(AgentModel model, List<AgentTool> tools, Predicate<String> permission, int maxRounds, int maxResultChars) {
        this.model = model;
        tools.forEach(t -> this.tools.put(t.name(), t));
        this.permission = permission;
        this.maxRounds = Math.max(1, maxRounds);
        this.maxResultChars = Math.max(500, maxResultChars);
    }

    /** 发给大模型的工具定义：只包含当前用户有权限的工具 */
    ArrayNode toolDefinitions() {
        ArrayNode arr = json.createArrayNode();
        for (AgentTool t : tools.values()) {
            if (!permission.test(t.permission())) {
                continue;
            }
            ObjectNode fn = arr.addObject().put("type", "function").putObject("function");
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.set("parameters", t.parameters());
        }
        return arr;
    }

    public AgentResult run(String systemPrompt, List<ChatMessage> history, String question, AgentListener listener) {
        ArrayNode messages = json.createArrayNode();
        messages.addObject().put("role", "system").put("content", systemPrompt);
        if (history != null) {
            for (ChatMessage m : history) {
                messages.addObject().put("role", m.role()).put("content", m.content());
            }
        }
        messages.addObject().put("role", "user").put("content", question);

        ArrayNode toolDefs = toolDefinitions();
        List<AgentResult.Step> steps = new ArrayList<>();
        Map<String, String> cache = new HashMap<>();
        int tokens = 0;

        for (int round = 1; round <= maxRounds + 1; round++) {
            boolean last = round > maxRounds;
            if (last) {
                // 到达上限：追加一条提示，并且不再提供工具，强制模型给出回答
                messages.addObject().put("role", "user").put("content", LIMIT_PROMPT);
            }
            listener.thinking(round);
            ModelReply reply = model.call(messages, last || toolDefs.isEmpty() ? null : toolDefs);
            tokens += reply.totalTokens();
            ObjectNode msg = reply.message();
            JsonNode calls = msg.path("tool_calls");

            if (last || !calls.isArray() || calls.isEmpty()) {
                String answer = msg.path("content").asText("").trim();
                if (answer.isEmpty()) {
                    answer = "抱歉，没有得到有效的回答，请换个问法再试。";
                }
                listener.answer(answer);
                return new AgentResult(answer, steps, round, tokens, last);
            }

            // 必须把带 tool_calls 的 assistant 消息原样放回对话，后面的 tool 消息靠 tool_call_id 和它对应
            ObjectNode assistant = messages.addObject();
            assistant.put("role", "assistant");
            assistant.put("content", msg.path("content").asText(""));
            assistant.set("tool_calls", calls.deepCopy());

            int index = 0;
            for (JsonNode call : calls) {
                index++;
                String callId = call.path("id").asText("call_" + round + "_" + index);
                String name = call.path("function").path("name").asText("");
                String rawArgs = call.path("function").path("arguments").asText("");
                String content;
                if (index > MAX_CALLS_PER_ROUND) {
                    content = error("一次最多调用 " + MAX_CALLS_PER_ROUND + " 个工具，这个调用被忽略");
                } else {
                    content = execute(callId, name, rawArgs, cache, steps, listener);
                }
                messages.addObject().put("role", "tool").put("tool_call_id", callId).put("content", content);
            }
        }
        // 循环里最后一轮一定会 return，这里只是让编译器满意
        throw new IllegalStateException("unreachable");
    }

    /** 执行一个工具，返回给大模型的内容（JSON 字符串） */
    private String execute(String callId, String name, String rawArgs, Map<String, String> cache,
                           List<AgentResult.Step> steps, AgentListener listener) {
        AgentTool tool = tools.get(name);
        String label = tool == null ? name : tool.label();
        JsonNode args;
        try {
            args = rawArgs == null || rawArgs.isBlank() ? json.createObjectNode() : json.readTree(rawArgs);
        } catch (JsonProcessingException e) {
            args = null;
        }
        listener.toolStart(callId, name, label, args == null ? json.createObjectNode() : args);
        long start = System.currentTimeMillis();

        String content;
        String summary;
        boolean ok = false;
        String cacheKey = name + "|" + (args == null ? rawArgs : args.toString());
        if (tool == null) {
            summary = "没有这个工具";
            content = error("没有名为 " + name + " 的工具");
        } else if (!permission.test(tool.permission())) {
            // 正常不会出现（没权限的工具不会发给模型），防止模型“猜”出工具名来调用
            summary = "没有权限";
            content = error("当前用户没有权限使用这个工具");
        } else if (args == null || !args.isObject()) {
            summary = "参数格式错误";
            content = error("参数必须是 JSON 对象");
        } else if (cache.containsKey(cacheKey)) {
            ok = true;
            summary = "与之前的查询相同，直接复用结果";
            content = cache.get(cacheKey);
        } else {
            try {
                ToolResult r = tool.execute(args);
                ok = true;
                summary = r.summary();
                content = truncate(json.writeValueAsString(r.data()));
                cache.put(cacheKey, content);
            } catch (IllegalArgumentException | BizException e) {
                summary = e.getMessage();
                content = error(e.getMessage());
            } catch (Exception e) {
                log.warn("智能体工具 {} 执行失败", name, e);
                summary = "执行出错";
                content = error("工具执行出错，请稍后再试");
            }
        }
        long cost = System.currentTimeMillis() - start;
        steps.add(new AgentResult.Step(name, label, args == null ? rawArgs : args.toString(), ok, summary, cost));
        listener.toolEnd(callId, name, ok, summary, cost);
        return content;
    }

    private String error(String msg) {
        return json.createObjectNode().put("error", msg).toString();
    }

    private String truncate(String s) {
        return s.length() <= maxResultChars ? s : s.substring(0, maxResultChars) + "…（结果过长，已截断）";
    }
}
