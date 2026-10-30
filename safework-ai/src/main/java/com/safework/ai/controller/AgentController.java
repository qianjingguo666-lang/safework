package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.agent.AgentListener;
import com.safework.ai.agent.AgentResult;
import com.safework.ai.agent.AgentService;
import com.safework.ai.agent.action.ActionResultVO;
import com.safework.ai.agent.action.PendingActionService;
import com.safework.common.annotation.OperLog;
import com.safework.ai.dto.AgentToolVO;
import com.safework.ai.dto.ChatRequest;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "AI 智能体", description = "大模型通过 Function Calling 调用查询工具，基于真实业务数据回答")
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final PendingActionService pendingActions;
    private final ObjectMapper json = new ObjectMapper();

    @SaCheckPermission("ai:agent")
    @Operation(summary = "当前用户可用的工具")
    @GetMapping("/tools")
    public R<List<AgentToolVO>> tools() {
        return R.ok(agentService.availableTools());
    }

    /**
     * 智能体问答，SSE 推送执行过程。事件：
     * thinking（第几轮）→ tool_start / tool_end（每次工具调用）→ … → answer（最终回答）→ done；出错时发 error
     * <p>
     * 直接在请求线程里往 response 写事件并 flush（不用 SseEmitter 异步线程），
     * 这样工具执行时登录信息、租户、数据权限都还在。
     */
    @SaCheckPermission("ai:agent")
    @Operation(summary = "智能体问答（SSE：thinking → tool_start/tool_end/confirm… → answer → done）")
    @PostMapping("/chat")
    public void chat(@Valid @RequestBody ChatRequest req, HttpServletResponse response) throws IOException {
        // 检查不通过时抛异常，由全局异常处理器以 JSON 返回（此时还没开始写流）
        agentService.precheck();

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        // 告诉 Nginx 不要缓冲，每个事件立即发给浏览器
        response.setHeader("X-Accel-Buffering", "no");
        ServletOutputStream out = response.getOutputStream();
        long start = System.currentTimeMillis();

        AgentListener listener = new AgentListener() {
            @Override
            public void thinking(int round) {
                send(out, "thinking", Map.of("round", round));
            }

            @Override
            public void toolStart(String callId, String name, String label, JsonNode args) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", callId);
                m.put("name", name);
                m.put("label", label);
                m.put("args", args);
                send(out, "tool_start", m);
            }

            @Override
            public void toolEnd(String callId, String name, boolean ok, String summary, long costMs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", callId);
                m.put("name", name);
                m.put("ok", ok);
                m.put("summary", summary);
                m.put("costMs", costMs);
                send(out, "tool_end", m);
            }

            @Override
            public void answer(String text) {
                send(out, "answer", Map.of("t", text));
            }

            @Override
            public void confirm(Object card) {
                send(out, "confirm", card);
            }
        };

        try {
            AgentResult r = agentService.run(req, listener);
            send(out, "done", Map.of("rounds", r.rounds(), "tokens", r.totalTokens(), "limited", r.limited(),
                    "costMs", System.currentTimeMillis() - start));
        } catch (ClientGoneException e) {
            log.debug("智能体：浏览器已断开");
        } catch (Exception e) {
            log.warn("智能体执行失败：{}", e.getMessage());
            try {
                send(out, "error", Map.of("msg", e.getMessage() == null ? "AI 服务异常" : e.getMessage()));
            } catch (ClientGoneException ignore) {
                // 浏览器已经断开
            }
        }
    }

    /** 用户核对确认卡片后点击【确认执行】：这时才真正修改数据 */
    @OperLog(module = "AI 智能体", action = "确认执行")
    @SaCheckPermission("ai:agent")
    @Operation(summary = "确认执行智能体生成的操作")
    @PostMapping("/actions/{id}/confirm")
    public R<ActionResultVO> confirm(@PathVariable String id) {
        return R.ok(pendingActions.confirm(id));
    }

    @SaCheckPermission("ai:agent")
    @Operation(summary = "取消智能体生成的操作")
    @PostMapping("/actions/{id}/cancel")
    public R<Void> cancel(@PathVariable String id) {
        pendingActions.cancel(id);
        return R.ok();
    }

    private void send(ServletOutputStream out, String event, Object data) {
        try {
            // data 先转成一行 JSON：回答里的换行如果直接写进 SSE 会破坏格式
            String frame = "event:" + event + "\ndata:" + json.writeValueAsString(data) + "\n\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            // 用户关闭页面或点了停止：抛出去，让智能体停止后续的大模型调用，节省费用
            throw new ClientGoneException();
        }
    }

    /** 浏览器断开连接 */
    static class ClientGoneException extends RuntimeException {
    }
}
