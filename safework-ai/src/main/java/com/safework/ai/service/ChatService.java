package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.dto.ChatLogQueryDTO;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.dto.ChatRequest;
import com.safework.ai.dto.SourceVO;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.common.tenant.TenantContext;
import com.safework.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库问答（RAG）：检索 → 拼提示词 → 大模型流式生成 → 记录。
 *
 * <pre>
 * 线程模型：
 *   请求线程：限流、检索（需要租户上下文和登录信息），然后立即返回 SseEmitter
 *   aiExecutor 线程：调用大模型，边生成边推送给浏览器；结束后写问答记录
 *   注意 ThreadLocal 不会自动传到新线程，所以租户 ID、用户信息要在请求线程里先取出来
 * </pre>
 */
@Slf4j
@Service
public class ChatService {

    static final String SYSTEM_PROMPT = """
            你是企业安全生产助手，负责回答员工关于安全制度、作业规程的问题。
            规则：
            1. 只能根据【参考资料】回答，不要使用资料以外的知识，更不能编造数值和条款；
            2. 资料里没有答案时，直接说“知识库中没有找到相关规定”，并建议咨询安全管理人员；
            3. 回答要简洁、分条列出，涉及数值（浓度、时间）时原样引用；
            4. 在引用资料的句子后面用 [1]、[2] 标注来源编号。""";
    static final String NO_HIT = "知识库中没有找到相关规定。可以换个说法再问，或者联系安全管理人员补充制度文件。";

    private final RetrievalService retrieval;
    private final LlmClient llm;
    private final AiProperties props;
    private final AiRateLimiter rateLimiter;
    private final AiChatLogMapper chatLogMapper;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectMapper json = new ObjectMapper();

    public ChatService(RetrievalService retrieval, LlmClient llm, AiProperties props, AiRateLimiter rateLimiter,
                       AiChatLogMapper chatLogMapper, @Qualifier("aiExecutor") ThreadPoolTaskExecutor executor) {
        this.retrieval = retrieval;
        this.llm = llm;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.chatLogMapper = chatLogMapper;
        this.executor = executor;
    }

    public SseEmitter stream(ChatRequest req) {
        long start = System.currentTimeMillis();
        Long userId = StpUtil.getLoginIdAsLong();
        rateLimiter.check(userId);
        Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        Long tenantId = TenantContext.get();
        String question = req.getQuestion().trim();

        // 追问（如“那动火作业呢？”）单独检索效果差，把上一个问题拼进来一起检索
        String lastQuestion = lastUserMessage(req.getHistory());
        List<SourceVO> sources = retrieval.retrieve(
                lastQuestion == null ? question : lastQuestion + " " + question, props.getRag().getTopK());

        SseEmitter emitter = new SseEmitter(180_000L);
        try {
            executor.execute(() -> {
                StringBuilder answer = new StringBuilder();
                String error = null;
                try {
                    send(emitter, "sources", sources);
                    if (sources.isEmpty()) {
                        answer.append(NO_HIT);
                        send(emitter, "delta", Map.of("t", NO_HIT));
                    } else if (!llm.chatEnabled()) {
                        String tip = "（当前未配置大模型，以下是知识库中最相关的原文，请参考右侧来源。）";
                        answer.append(tip);
                        send(emitter, "delta", Map.of("t", tip));
                    } else {
                        llm.chatStream(buildMessages(question, req.getHistory(), sources), delta -> {
                            answer.append(delta);
                            send(emitter, "delta", Map.of("t", delta));
                        });
                    }
                    send(emitter, "done", Map.of("hit", !sources.isEmpty(), "costMs", System.currentTimeMillis() - start));
                    emitter.complete();
                } catch (ClientGoneException e) {
                    // 用户关闭了页面或点了停止，不算错误
                    error = "用户中断";
                } catch (Exception e) {
                    error = e.getMessage();
                    log.warn("AI 问答失败：{}", e.getMessage());
                    try {
                        send(emitter, "error", Map.of("msg", error == null ? "AI 服务异常" : error));
                        emitter.complete();
                    } catch (Exception ignore) {
                        emitter.completeWithError(e);
                    }
                } finally {
                    saveLog(tenantId, userId, nickname, question, answer.toString(), sources, error, start);
                }
            });
        } catch (TaskRejectedException e) {
            throw new com.safework.common.exception.BizException(503, "当前提问的人太多，请稍后再试");
        }
        return emitter;
    }

    List<ChatMessage> buildMessages(String question, List<ChatMessage> history, List<SourceVO> sources) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        if (history != null) {
            // 只保留最近 6 条，每条截断，控制 token 成本
            history.stream()
                    .filter(m -> m != null && StringUtils.hasText(m.content())
                            && ("user".equals(m.role()) || "assistant".equals(m.role())))
                    .skip(Math.max(0, history.size() - 6))
                    .map(m -> new ChatMessage(m.role(), m.content().length() > 500 ? m.content().substring(0, 500) : m.content()))
                    .forEach(messages::add);
        }
        messages.add(ChatMessage.user("【参考资料】\n" + RetrievalService.formatContext(sources) + "【问题】\n" + question));
        return messages;
    }

    public Page<AiChatLog> logs(ChatLogQueryDTO q) {
        return chatLogMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<AiChatLog>lambdaQuery()
                .eq(StringUtils.hasText(q.getBizType()), AiChatLog::getBizType, q.getBizType())
                .eq(q.getHit() != null, AiChatLog::getHit, q.getHit())
                .like(StringUtils.hasText(q.getKeyword()), AiChatLog::getQuestion, q.getKeyword())
                .orderByDesc(AiChatLog::getCreateTime));
    }

    /** 在 AI 线程里写库：手动设置租户上下文，用完清除（线程池会复用线程） */
    void saveLog(Long tenantId, Long userId, Object nickname, String question, String answer,
                 List<SourceVO> sources, String error, long start) {
        TenantContext.set(tenantId);
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.CHAT);
            row.setUserId(userId);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion(question);
            row.setAnswer(answer);
            row.setSources(json.writeValueAsString(sources.stream()
                    .map(s -> Map.of("title", s.documentTitle(), "page", s.pageNo() == null ? "" : s.pageNo(), "score", s.score()))
                    .toList()));
            row.setHit(sources.isEmpty() ? 0 : 1);
            row.setModel(llm.chatEnabled() ? props.getChat().getModel() : "仅检索");
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存问答记录失败：{}", e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            // 先转成 JSON 字符串：回答里的换行如果直接写进 SSE 会破坏格式
            emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data)));
        } catch (IOException e) {
            throw new ClientGoneException();
        }
    }

    private static String lastUserMessage(List<ChatMessage> history) {
        if (history == null) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m != null && "user".equals(m.role()) && StringUtils.hasText(m.content())) {
                return m.content().length() > 200 ? m.content().substring(0, 200) : m.content();
            }
        }
        return null;
    }

    /** 浏览器断开连接 */
    static class ClientGoneException extends RuntimeException {
    }
}
