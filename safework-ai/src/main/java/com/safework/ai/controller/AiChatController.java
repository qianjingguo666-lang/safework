package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.dto.AiStatusVO;
import com.safework.ai.dto.ChatLogQueryDTO;
import com.safework.ai.dto.ChatRequest;
import com.safework.ai.dto.ReviewResultVO;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.service.ChatService;
import com.safework.ai.service.KnowledgeService;
import com.safework.ai.service.TicketReviewService;
import com.safework.common.core.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final ChatService chatService;
    private final TicketReviewService reviewService;
    private final KnowledgeService knowledgeService;
    private final LlmClient llm;
    private final AiProperties props;

    /** AI 配置状态：前端据此提示“未配置大模型” */
    @GetMapping("/status")
    public R<AiStatusVO> status() {
        return R.ok(new AiStatusVO(llm.chatEnabled(), props.getChat().getModel(),
                llm.embeddingEnabled(), props.getEmbedding().getModel(),
                knowledgeService.documentCount(), knowledgeService.chunkCount()));
    }

    /**
     * 流式问答（SSE）。事件：
     * sources 引用片段 → delta 回答片段（多次）→ done 结束；出错时发 error
     */
    @SaCheckPermission("ai:chat")
    // 不写 produces：返回 SseEmitter 时 Spring 会自动设置 text/event-stream；
    // 写了的话，限流等异常就无法按 JSON 返回（会变成 406）
    @PostMapping("/chat/stream")
    public SseEmitter stream(@Valid @RequestBody ChatRequest req) {
        return chatService.stream(req);
    }

    @SaCheckPermission("ai:knowledge:list")
    @GetMapping("/chat/logs")
    public R<Page<AiChatLog>> logs(ChatLogQueryDTO query) {
        return R.ok(chatService.logs(query));
    }

    @SaCheckPermission("ai:ticket:review")
    @PostMapping("/ticket/{id}/review")
    public R<ReviewResultVO> review(@PathVariable Long id) {
        return R.ok(reviewService.review(id));
    }
}
