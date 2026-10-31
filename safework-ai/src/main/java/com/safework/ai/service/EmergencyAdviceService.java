package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.emergency.EmergencyAdvice;
import com.safework.ai.core.emergency.EmergencyMatcher;
import com.safework.ai.core.emergency.PlanCandidate;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.dto.EmergencyAdviceVO;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.safety.emergency.EmergencyRules;
import com.safework.safety.emergency.EmergencyService;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyPlan;
import com.safework.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 应急助手：根据现场描述匹配预案，从预案原文里提炼“现在立刻要做的几件事”。
 * 事故现场分秒必争，没人有时间翻几十页的预案；但处置要点必须来自预案原文，不能让大模型自由发挥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyAdviceService {

    /** 每个预案最多发给大模型多少字 */
    static final int MAX_PLAN_CHARS = 2500;

    static final String SYSTEM_PROMPT = """
            你是化工企业的应急指挥助手。现场刚发生突发事件，请从【候选预案】中选出最适合启动的一个，
            并从该预案原文中提炼出现场人员“现在立刻要做的事”。
            要求：
            1. planCode 只能从候选预案的编号中选择；都不合适时选最接近的一个；
            2. steps：3~8 条，按先后顺序，每条不超过 40 字，必须来自所选预案的原文，不要编造预案里没有的措施；
            3. cautions：最多 4 条，摘出预案中的禁止事项（例如严禁盲目施救），没有就返回空数组；
            4. reason：一句话说明为什么选这个预案，不超过 40 字。
            只输出 JSON，不要输出其他内容，格式：
            {"planCode":"","reason":"","steps":["…"],"cautions":["…"]}""";

    private final EmergencyService emergencyService;
    private final LlmClient llm;
    private final AiProperties props;
    private final AiRateLimiter rateLimiter;
    private final AiChatLogMapper chatLogMapper;

    public EmergencyAdviceVO advise(Long eventId) {
        long start = System.currentTimeMillis();
        EmergencyEvent e = emergencyService.detail(eventId).event();
        List<EmergencyPlan> plans = emergencyService.enabledPlans();
        List<PlanCandidate> all = plans.stream().map(p -> new PlanCandidate(p.getId(), p.getPlanCode(), p.getTitle(),
                p.getPlanType(), p.getAccidentType(), p.getScope(), p.getContent())).toList();
        String text = e.getTitle() + " " + e.getLocation() + " " + (e.getDescription() == null ? "" : e.getDescription());
        List<PlanCandidate> candidates = new ArrayList<>(EmergencyMatcher.candidates(e.getAccidentType(), text, all));
        // 一个都匹配不上时，用综合应急预案
        if (candidates.isEmpty()) {
            all.stream().filter(p -> EmergencyRules.PLAN_COMPREHENSIVE.equals(p.planType())).findFirst().ifPresent(candidates::add);
        }

        EmergencyAdvice advice = null;
        String reply = null;
        String error = null;
        String note = null;
        if (candidates.isEmpty()) {
            note = "预案库里还没有启用的预案";
        } else if (llm.chatEnabled()) {
            rateLimiter.check(StpUtil.getLoginIdAsLong());
            try {
                reply = llm.chat(List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(userPrompt(e, candidates))), 40);
                advice = EmergencyMatcher.parse(reply, candidates);
                if (advice == null) {
                    error = "模型返回的格式不对或编造了预案编号";
                }
            } catch (RuntimeException ex) {
                error = ex.getMessage();
                log.warn("应急建议调用大模型失败：{}", ex.getMessage());
            }
            if (advice == null) {
                note = "大模型暂时不可用，以下是按事故类型匹配、从预案原文摘出的步骤";
            }
        } else {
            note = "没有配置大模型，以下是按事故类型匹配、从预案原文摘出的步骤";
        }
        boolean aiUsed = advice != null;
        if (advice == null) {
            advice = EmergencyMatcher.fallback(candidates);
        }
        PlanCandidate p = advice.plan();
        EmergencyAdviceVO vo = new EmergencyAdviceVO(p == null ? null : p.id(), p == null ? null : p.code(),
                p == null ? null : p.title(), advice.reason(), advice.steps(), advice.cautions(),
                candidates.stream().map(c -> new EmergencyAdviceVO.Candidate(c.id(), c.code(), c.title())).toList(),
                aiUsed, note, System.currentTimeMillis() - start);
        saveLog(e, reply, vo, error, start);
        return vo;
    }

    static String userPrompt(EmergencyEvent e, List<PlanCandidate> candidates) {
        StringBuilder sb = new StringBuilder("【现场情况】\n")
                .append("事件：").append(e.getTitle()).append('\n')
                .append("类型：").append(EmergencyRules.ACCIDENT_TYPES.getOrDefault(e.getAccidentType(), e.getAccidentType()))
                .append("，等级：").append(EmergencyRules.LEVEL_LABELS.getOrDefault(e.getLevel(), e.getLevel())).append('\n')
                .append("地点：").append(e.getLocation()).append('\n')
                .append("伤亡：死亡 ").append(e.getDead()).append(" 人，重伤 ").append(e.getSeriousInjured())
                .append(" 人，轻伤 ").append(e.getMinorInjured()).append(" 人\n");
        if (e.getDescription() != null && !e.getDescription().isBlank()) {
            sb.append("描述：").append(e.getDescription()).append('\n');
        }
        sb.append("\n【候选预案】\n");
        for (PlanCandidate c : candidates) {
            String content = c.content() == null ? "" : c.content();
            sb.append("编号：").append(c.code()).append("　名称：").append(c.title())
                    .append("　适用范围：").append(c.scope() == null ? "" : c.scope()).append('\n')
                    .append(content.length() > MAX_PLAN_CHARS ? content.substring(0, MAX_PLAN_CHARS) : content)
                    .append("\n\n");
        }
        return sb.toString();
    }

    private void saveLog(EmergencyEvent e, String reply, EmergencyAdviceVO vo, String error, long start) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.EMERGENCY);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion("应急建议：" + e.getEventNo() + " " + e.getTitle());
            row.setAnswer(reply == null ? null : (reply.length() > 4000 ? reply.substring(0, 4000) : reply));
            row.setSources(vo.planCode() == null ? "未匹配到预案" : "建议启动 " + vo.planCode() + " " + vo.planTitle());
            row.setHit(vo.planId() == null ? 0 : 1);
            row.setModel(vo.aiUsed() ? props.getChat().getModel() : "rule");
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("保存 AI 调用记录失败：{}", ex.getMessage());
        }
    }
}
