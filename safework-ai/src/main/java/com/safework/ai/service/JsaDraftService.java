package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.jsa.JsaDraft;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.common.exception.BizException;
import com.safework.system.service.AuthService;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.jsa.JsaRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * AI 起草 JSA：根据作业类型、名称、内容、地点拆步骤、识别危害、评估风险、写控制措施。
 * 大模型没配置或调用失败时按作业类型的模板起草。结果只是草稿，编写人修改后保存、提交审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsaDraftService {

    /** 结果；check 是按发布规则校验的结果（null 表示可以直接保存） */
    public record Result(List<JsaRules.Step> steps, String ppe, boolean aiUsed, String note, String check) {
    }

    private final LlmClient llm;
    private final AiProperties props;
    private final AiRateLimiter rateLimiter;
    private final AiChatLogMapper chatLogMapper;

    public Result draft(String ticketType, String title, String workContent, String location) {
        if (!StringUtils.hasText(title) || !StringUtils.hasText(workContent)) {
            throw new BizException("请先填写 JSA 名称和作业内容，AI 才知道要分析什么作业");
        }
        TicketType type = StringUtils.hasText(ticketType) ? Arrays.stream(TicketType.values()).filter(t -> t.name().equals(ticketType)).findFirst()
                .orElseThrow(() -> new BizException("作业类型不正确")) : null;
        long start = System.currentTimeMillis();
        String prompt = JsaDraft.userPrompt(type == null ? null : type.getLabel(), title, workContent, location);
        JsaDraft.Draft d = null;
        String reply = null;
        String error = null;
        String note = null;
        if (llm.chatEnabled()) {
            rateLimiter.check(StpUtil.getLoginIdAsLong());
            try {
                reply = llm.chat(List.of(ChatMessage.system(JsaDraft.SYSTEM_PROMPT), ChatMessage.user(prompt)), 60);
                d = JsaDraft.parse(reply);
                if (d == null) {
                    error = "模型返回的格式不对";
                }
            } catch (RuntimeException ex) {
                error = ex.getMessage();
                log.warn("JSA 起草调用大模型失败：{}", ex.getMessage());
            }
            if (d == null) {
                note = "大模型暂时不可用，以下是按作业类型的模板起草的，请结合现场修改";
            }
        } else {
            note = "没有配置大模型，以下是按作业类型的模板起草的，请结合现场修改";
        }
        boolean aiUsed = d != null;
        if (d == null) {
            d = JsaDraft.fallback(ticketType);
        }
        Result r = new Result(d.steps(), d.ppe(), aiUsed, note, JsaRules.checkSteps(d.steps()));
        saveLog(title, reply, r, error, start);
        return r;
    }

    private void saveLog(String title, String reply, Result r, String error, long start) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.JSA);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion("JSA 起草：" + title);
            String answer = reply != null ? reply : r.steps().size() + " 个步骤（模板）";
            row.setAnswer(answer.length() > 4000 ? answer.substring(0, 4000) : answer);
            row.setSources(r.steps().size() + " 个步骤");
            row.setHit(1);
            row.setModel(r.aiUsed() ? props.getChat().getModel() : "rule");
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("保存 AI 调用记录失败：{}", ex.getMessage());
        }
    }
}
