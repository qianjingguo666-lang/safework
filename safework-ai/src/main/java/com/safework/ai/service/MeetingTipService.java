package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.meeting.MeetingTips;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.MeetingTeam;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.safety.meeting.MeetingService;
import com.safework.system.service.AuthService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 班前会助手：把今天的作业票、重点风险点、未整改隐患交给大模型，写成班组长能直接念的安全要点。
 * 大模型没配置或调用失败时按规则生成，结果都可以由班组长修改后再保存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingTipService {

    /** 结果 */
    public record Tips(List<String> tips, String text, List<String> works, boolean aiUsed, String note) {
    }

    private final MeetingService meetingService;
    private final WorkTicketMapper ticketMapper;
    private final RiskPointMapper riskPointMapper;
    private final HazardMapper hazardMapper;
    private final LlmClient llm;
    private final AiProperties props;
    private final AiRateLimiter rateLimiter;
    private final AiChatLogMapper chatLogMapper;

    public Tips generate(Long teamId) {
        long start = System.currentTimeMillis();
        MeetingTeam team = meetingService.teamForHost(teamId);
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<MeetingTips.Work> works = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                        .in(WorkTicket::getStatus, TicketStatus.APPROVED, TicketStatus.IN_PROGRESS)
                        .lt(WorkTicket::getPlanStartTime, dayStart.plusDays(1)).ge(WorkTicket::getPlanEndTime, dayStart)
                        .orderByAsc(WorkTicket::getPlanStartTime).last("LIMIT 10"))
                .stream().map(t -> new MeetingTips.Work(t.getTicketType().name(), t.getTicketType().getLabel(), t.getTitle(), t.getLocation())).toList();
        List<MeetingTips.Risk> risks = riskPointMapper.selectList(Wrappers.<RiskPoint>lambdaQuery()
                        .eq(RiskPoint::getStatus, 1).in(RiskPoint::getRiskLevel, RiskLevel.MAJOR, RiskLevel.HIGH)
                        .orderByDesc(RiskPoint::getLecD).last("LIMIT 5"))
                .stream().map(r -> new MeetingTips.Risk(r.getPointName(), r.getRiskLevel().getLabel(), r.getControlMeasures())).toList();
        int hazards = Math.toIntExact(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .in(Hazard::getStatus, HazardStatus.REPORTED, HazardStatus.RECTIFYING, HazardStatus.PENDING_VERIFY)));

        List<String> tips = null;
        String reply = null;
        String error = null;
        String note = null;
        if (llm.chatEnabled()) {
            rateLimiter.check(StpUtil.getLoginIdAsLong());
            try {
                reply = llm.chat(List.of(ChatMessage.system(MeetingTips.SYSTEM_PROMPT),
                        ChatMessage.user(MeetingTips.userPrompt(team.getName(), works, risks, hazards))), 30);
                tips = MeetingTips.parse(reply);
                if (tips == null) {
                    error = "模型返回的格式不对";
                }
            } catch (RuntimeException ex) {
                error = ex.getMessage();
                log.warn("班前会要点调用大模型失败：{}", ex.getMessage());
            }
            if (tips == null) {
                note = "大模型暂时不可用，以下是按今天的作业票和风险点自动整理的要点";
            }
        } else {
            note = "没有配置大模型，以下是按今天的作业票和风险点自动整理的要点";
        }
        boolean aiUsed = tips != null;
        if (tips == null) {
            tips = MeetingTips.fallback(works, risks, hazards);
        }
        Tips result = new Tips(tips, MeetingTips.join(tips),
                works.stream().map(w -> w.typeLabel() + "：" + w.title() + "（" + w.location() + "）").toList(), aiUsed, note);
        saveLog(team, reply, result, error, start);
        return result;
    }

    private void saveLog(MeetingTeam team, String reply, Tips t, String error, long start) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.MEETING);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion("班前会安全要点：" + team.getName());
            row.setAnswer(reply == null ? t.text() : (reply.length() > 4000 ? reply.substring(0, 4000) : reply));
            row.setSources("今天作业票 " + t.works().size() + " 张");
            row.setHit(t.works().isEmpty() ? 0 : 1);
            row.setModel(t.aiUsed() ? props.getChat().getModel() : "rule");
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception ex) {
            log.warn("保存 AI 调用记录失败：{}", ex.getMessage());
        }
    }
}
