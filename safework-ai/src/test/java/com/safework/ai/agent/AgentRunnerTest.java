package com.safework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用“剧本”代替真实大模型：按顺序返回预先写好的回复，并记录每次收到的对话和工具，
 * 不花钱、结果稳定，可以精确验证循环的每一步。
 */
@DisplayName("智能体循环（Function Calling）")
class AgentRunnerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 剧本模型 */
    static class ScriptedModel implements AgentModel {
        final Deque<ObjectNode> replies = new ArrayDeque<>();
        final List<ArrayNode> seenMessages = new ArrayList<>();
        final List<ArrayNode> seenTools = new ArrayList<>();

        ScriptedModel answer(String text) {
            replies.add(JSON.createObjectNode().put("role", "assistant").put("content", text));
            return this;
        }

        /** 一轮里调用若干工具：每个元素是 {函数名, 参数 JSON} */
        ScriptedModel call(String[]... calls) {
            ObjectNode msg = JSON.createObjectNode().put("role", "assistant").putNull("content");
            ArrayNode arr = msg.putArray("tool_calls");
            for (String[] c : calls) {
                ObjectNode tc = arr.addObject().put("id", "call_" + (seenMessages.size() + replies.size()) + "_" + arr.size()).put("type", "function");
                tc.putObject("function").put("name", c[0]).put("arguments", c[1]);
            }
            replies.add(msg);
            return this;
        }

        @Override
        public ModelReply call(ArrayNode messages, ArrayNode tools) {
            seenMessages.add(messages.deepCopy());
            seenTools.add(tools == null ? null : tools.deepCopy());
            ObjectNode next = replies.isEmpty()
                    ? JSON.createObjectNode().put("role", "assistant").put("content", "（剧本用完）")
                    : replies.poll();
            return new ModelReply(next, 100);
        }
    }

    /** 测试工具：记录执行次数；参数 fail=true 时模拟参数错误 */
    static class FakeTool implements AgentTool {
        final String name;
        final String perm;
        final AtomicInteger runs = new AtomicInteger();
        Object data = Map.of("total", 2);

        FakeTool(String name, String perm) {
            this.name = name;
            this.perm = perm;
        }

        public String name() { return name; }
        public String label() { return "测试" + name; }
        public String description() { return "测试工具"; }
        public ObjectNode parameters() { return ToolSchema.object().integer("limit", "条数", 1, 10).build(); }
        public String permission() { return perm; }

        public ToolResult execute(JsonNode args) {
            runs.incrementAndGet();
            if (args.path("fail").asBoolean()) {
                throw new IllegalArgumentException("参数 status 的取值 X 无效");
            }
            return new ToolResult(data, "共 2 条");
        }
    }

    private ScriptedModel model;
    private FakeTool hazards;
    private FakeTool secret;
    private final List<String> events = new ArrayList<>();
    private final AgentListener listener = new AgentListener() {
        public void thinking(int round) { events.add("thinking:" + round); }
        public void toolStart(String id, String name, String label, JsonNode args) { events.add("start:" + name); }
        public void toolEnd(String id, String name, boolean ok, String summary, long cost) { events.add("end:" + name + ":" + ok); }
        public void answer(String text) { events.add("answer"); }
        public void confirm(Object card) { events.add("confirm:" + card); }
    };

    @BeforeEach
    void setUp() {
        model = new ScriptedModel();
        hazards = new FakeTool("search_hazards", "safety:hazard:list");
        secret = new FakeTool("search_secret", "secret:view");
    }

    private AgentRunner runner(int maxRounds) {
        Set<String> granted = Set.of("safety:hazard:list");
        return new AgentRunner(model, List.of(hazards, secret), granted::contains, maxRounds, 6000);
    }

    @Test
    @DisplayName("不需要查数据的问题：一轮直接回答，不调用工具")
    void directAnswer() {
        model.answer("你好，我可以帮你查隐患。");
        AgentResult r = runner(5).run("系统提示", List.of(), "你好", listener);
        assertEquals("你好，我可以帮你查隐患。", r.answer());
        assertEquals(1, r.rounds());
        assertTrue(r.steps().isEmpty());
        assertEquals(List.of("thinking:1", "answer"), events);
    }

    @Test
    @DisplayName("调用工具后回答：结果以 role=tool 回传，tool_call_id 对得上，token 累加")
    void toolThenAnswer() {
        model.call(new String[]{"search_hazards", "{\"limit\":3}"}).answer("目前有 2 条隐患。");
        AgentResult r = runner(5).run("系统提示", List.of(new ChatMessage("user", "上一个问题")), "有几条隐患？", listener);

        assertEquals("目前有 2 条隐患。", r.answer());
        assertEquals(2, r.rounds());
        assertEquals(200, r.totalTokens());
        assertEquals(1, hazards.runs.get());
        assertEquals(1, r.steps().size());
        assertTrue(r.steps().get(0).ok());
        assertEquals(List.of("thinking:1", "start:search_hazards", "end:search_hazards:true", "thinking:2", "answer"), events);

        // 第二次调用模型时的对话：system、历史、问题、assistant(tool_calls)、tool
        ArrayNode second = model.seenMessages.get(1);
        assertEquals(5, second.size());
        assertEquals("system", second.get(0).path("role").asText());
        assertEquals("assistant", second.get(3).path("role").asText());
        String callId = second.get(3).path("tool_calls").get(0).path("id").asText();
        JsonNode tool = second.get(4);
        assertEquals("tool", tool.path("role").asText());
        assertEquals(callId, tool.path("tool_call_id").asText());
        assertTrue(tool.path("content").asText().contains("\"total\":2"));
    }

    @Test
    @DisplayName("没有权限的工具不会发给模型；模型硬调也会被拒绝，不会执行")
    void permission() {
        model.call(new String[]{"search_secret", "{}"}).answer("当前账号无法查询。");
        AgentResult r = runner(5).run("系统提示", null, "查机密", listener);

        ArrayNode tools = model.seenTools.get(0);
        assertEquals(1, tools.size());
        assertEquals("search_hazards", tools.get(0).path("function").path("name").asText());
        assertEquals(0, secret.runs.get());
        assertFalse(r.steps().get(0).ok());
        assertTrue(model.seenMessages.get(1).get(3).path("content").asText().contains("没有权限"));
    }

    @Test
    @DisplayName("工具不存在、参数不是 JSON、参数取值错误：把错误告诉模型，问答继续")
    void errorsGoBackToModel() {
        model.call(new String[]{"drop_table", "{}"},
                        new String[]{"search_hazards", "不是 JSON"},
                        new String[]{"search_hazards", "{\"fail\":true}"})
                .answer("参数有误，已说明。");
        AgentResult r = runner(5).run("系统提示", null, "测试", listener);

        assertEquals("参数有误，已说明。", r.answer());
        assertEquals(3, r.steps().size());
        r.steps().forEach(s -> assertFalse(s.ok()));
        ArrayNode msgs = model.seenMessages.get(1);
        assertTrue(msgs.get(3).path("content").asText().contains("没有名为 drop_table 的工具"));
        assertTrue(msgs.get(4).path("content").asText().contains("JSON 对象"));
        assertTrue(msgs.get(5).path("content").asText().contains("取值 X 无效"));
    }

    @Test
    @DisplayName("达到最大轮数：不再提供工具，追加提示，强制直接回答")
    void maxRounds() {
        model.call(new String[]{"search_hazards", "{\"limit\":1}"})
                .call(new String[]{"search_hazards", "{\"limit\":2}"})
                .answer("根据已查到的信息回答。");
        AgentResult r = runner(2).run("系统提示", null, "一直查", listener);

        assertTrue(r.limited());
        assertEquals(3, r.rounds());
        assertNull(model.seenTools.get(2));
        ArrayNode last = model.seenMessages.get(2);
        assertEquals(AgentRunner.LIMIT_PROMPT, last.get(last.size() - 1).path("content").asText());
        assertEquals("根据已查到的信息回答。", r.answer());
    }

    @Test
    @DisplayName("同样的工具和参数只执行一次；一轮最多执行 4 个")
    void dedupAndLimitPerRound() {
        model.call(new String[]{"search_hazards", "{\"limit\":3}"},
                        new String[]{"search_hazards", "{\"limit\":3}"},
                        new String[]{"search_hazards", "{\"limit\":4}"},
                        new String[]{"search_hazards", "{\"limit\":5}"},
                        new String[]{"search_hazards", "{\"limit\":6}"})
                .answer("好");
        runner(5).run("系统提示", null, "测试", listener);

        // 5 个调用：第 2 个复用第 1 个的结果，第 5 个超过每轮上限，实际执行 3 次
        assertEquals(3, hazards.runs.get());
        ArrayNode msgs = model.seenMessages.get(1);
        assertEquals(msgs.get(3).path("content").asText(), msgs.get(4).path("content").asText());
        assertTrue(msgs.get(7).path("content").asText().contains("一次最多调用"));
    }

    @Test
    @DisplayName("工具结果过长时截断，控制 token")
    void truncate() {
        hazards.data = Map.of("text", "很".repeat(5000));
        model.call(new String[]{"search_hazards", "{}"}).answer("好");
        new AgentRunner(model, List.of(hazards), p -> true, 5, 1000).run("系统提示", null, "测试", listener);
        String content = model.seenMessages.get(1).get(3).path("content").asText();
        assertTrue(content.length() < 1100);
        assertTrue(content.endsWith("已截断）"));
    }

    @Test
    @DisplayName("模型返回空内容时给出兜底提示")
    void emptyAnswer() {
        model.answer("  ");
        assertTrue(runner(5).run("系统提示", null, "测试", listener).answer().contains("换个问法"));
    }

    @Test
    @DisplayName("写操作返回确认卡片：推送 confirm 事件；同样的参数再调用一次会生成新卡片（不走缓存）")
    void actionCard() {
        AtomicInteger cards = new AtomicInteger();
        AgentTool action = new FakeTool("assign_hazard", "safety:hazard:list") {
            @Override
            public ToolResult execute(JsonNode args) {
                runs.incrementAndGet();
                return new ToolResult(Map.of("status", "WAITING_USER_CONFIRMATION"), "已生成确认卡片", "card-" + cards.incrementAndGet());
            }
        };
        model.call(new String[]{"assign_hazard", "{\"hazardNo\":\"YH1\"}"})
                .call(new String[]{"assign_hazard", "{\"hazardNo\":\"YH1\"}"})
                .answer("已生成确认卡片，请核对后点击确认。");
        new AgentRunner(model, List.of(action), p -> true, 5, 6000).run("系统提示", null, "指派", listener);
        assertEquals(List.of("thinking:1", "start:assign_hazard", "end:assign_hazard:true", "confirm:card-1",
                "thinking:2", "start:assign_hazard", "end:assign_hazard:true", "confirm:card-2", "thinking:3", "answer"), events);
        assertTrue(model.seenMessages.get(1).get(3).path("content").asText().contains("WAITING_USER_CONFIRMATION"));
    }
}
