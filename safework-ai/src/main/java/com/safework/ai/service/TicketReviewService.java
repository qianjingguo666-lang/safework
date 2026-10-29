package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.JsonExtractor;
import com.safework.ai.core.TicketRuleChecker;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.dto.ReviewResultVO;
import com.safework.ai.dto.SourceVO;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.service.SpaceService;
import com.safework.system.service.AuthService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 作业票 AI 预审：给审批人一个参考意见，不会自动改变作业票状态（最终由人决定）。
 * 两层：
 * 1. 规则检查：确定性的，稳定、零成本，大模型挂了也能用
 * 2. 大模型分析：结合检索到的制度条款，找规则覆盖不到的问题，要求输出固定 JSON 格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReviewService {

    static final String SYSTEM_PROMPT = """
            你是化工企业的安全审批专家，负责预审特殊作业票。请根据【企业制度】和【作业票】找出安全隐患。
            要求：
            1. 只指出确实存在的问题，不要泛泛而谈；【规则检查已发现】的问题不要重复；
            2. 每个问题给出等级：HIGH（必须整改才能作业）、MEDIUM（建议补充）、LOW（提示）；
            3. 只输出一个 JSON 对象，不要输出其他文字，格式：
            {"riskLevel":"HIGH|MEDIUM|LOW","conclusion":"建议通过|建议补充后再审批|建议驳回",
             "issues":[{"level":"MEDIUM","item":"检查项","problem":"问题","suggestion":"整改建议"}],
             "summary":"一句话总结"}""";

    private static final Map<String, String> TYPE_LABEL = Map.of(
            "CONFINED_SPACE", "受限空间作业", "HOT_WORK", "动火作业", "HEIGHT", "高处作业");

    private final TicketService ticketService;
    private final SpaceService spaceService;
    private final RetrievalService retrieval;
    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final AiChatLogMapper chatLogMapper;
    private final AiProperties props;
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ReviewResultVO review(Long ticketId) {
        long start = System.currentTimeMillis();
        rateLimiter.check(StpUtil.getLoginIdAsLong());
        // 复用作业票的数据权限：看不到的票也不能预审
        WorkTicket t = ticketService.detail(ticketId).getTicket();
        String type = t.getTicketType().name();

        List<TicketRuleChecker.Finding> rules = TicketRuleChecker.check(new TicketRuleChecker.Input(
                type, t.getSafetyMeasures(), num(t.getGasOxygen()), num(t.getGasLel()), num(t.getGasH2s()),
                t.getGasCheckTime(), t.getPlanStartTime(), t.getPlanEndTime(), LocalDateTime.now()));

        String medium = null;
        if (t.getSpaceId() != null) {
            try {
                ConfinedSpace space = spaceService.getById(t.getSpaceId());
                medium = space.getMedium();
            } catch (Exception ignore) {
                // 空间被删除不影响预审
            }
        }
        String typeLabel = TYPE_LABEL.getOrDefault(type, type);
        List<SourceVO> sources = retrieval.retrieve(typeLabel + " 安全措施 气体检测 监护 " + t.getTitle()
                + (medium == null ? "" : " " + medium), props.getRag().getTopK());

        ReviewResultVO vo = new ReviewResultVO();
        vo.setRuleFindings(rules);
        vo.setSources(sources);
        vo.setAiAvailable(llm.chatEnabled());
        String rawAnswer = null;
        if (llm.chatEnabled()) {
            try {
                rawAnswer = llm.chat(List.of(ChatMessage.system(SYSTEM_PROMPT),
                        ChatMessage.user(buildPrompt(t, typeLabel, medium, rules, sources))));
                String obj = JsonExtractor.firstObject(rawAnswer);
                if (obj == null) {
                    throw new IllegalStateException("模型没有按要求返回 JSON");
                }
                vo.setAi(json.readValue(obj, ReviewResultVO.AiReview.class));
            } catch (Exception e) {
                log.warn("作业票 {} AI 预审失败：{}", ticketId, e.getMessage());
                vo.setAiError(e.getMessage());
            }
        }
        vo.setRiskLevel(overallRisk(rules, vo.getAi()));
        vo.setCostMs(System.currentTimeMillis() - start);
        saveLog(t, vo, rawAnswer);
        return vo;
    }

    private String buildPrompt(WorkTicket t, String typeLabel, String medium,
                               List<TicketRuleChecker.Finding> rules, List<SourceVO> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("【企业制度】\n").append(sources.isEmpty() ? "（知识库中没有检索到相关制度）\n" : RetrievalService.formatContext(sources));
        sb.append("【作业票】\n")
                .append("类型：").append(typeLabel).append('\n')
                .append("名称：").append(t.getTitle()).append('\n')
                .append("地点：").append(t.getLocation()).append('\n');
        if (t.getSpaceName() != null) {
            sb.append("受限空间：").append(t.getSpaceName()).append(medium == null ? "" : "，危险介质：" + medium).append('\n');
        }
        sb.append("作业内容：").append(nvl(t.getWorkContent())).append('\n')
                .append("计划时间：").append(t.getPlanStartTime()).append(" 至 ").append(t.getPlanEndTime()).append('\n')
                .append("安全措施：").append(nvl(t.getSafetyMeasures())).append('\n');
        if (t.getGasOxygen() != null) {
            sb.append("气体检测：氧含量 ").append(t.getGasOxygen()).append("%，可燃气体 ").append(t.getGasLel())
                    .append("%LEL，硫化氢 ").append(t.getGasH2s() == null ? "未检测" : t.getGasH2s() + "mg/m³")
                    .append("，检测时间 ").append(t.getGasCheckTime()).append('\n');
        }
        sb.append("监护人：").append(t.getGuardianName()).append('\n');
        sb.append("【规则检查已发现】\n");
        if (rules.isEmpty()) {
            sb.append("无\n");
        }
        rules.forEach(r -> sb.append("- ").append(r.problem()).append('\n'));
        return sb.toString();
    }

    static String overallRisk(List<TicketRuleChecker.Finding> rules, ReviewResultVO.AiReview ai) {
        List<String> levels = new ArrayList<>(rules.stream().map(TicketRuleChecker.Finding::level).toList());
        if (ai != null) {
            levels.add(ai.getRiskLevel());
            if (ai.getIssues() != null) {
                ai.getIssues().forEach(i -> levels.add(i.level()));
            }
        }
        if (levels.contains("HIGH")) {
            return "HIGH";
        }
        return levels.contains("MEDIUM") ? "MEDIUM" : "LOW";
    }

    private void saveLog(WorkTicket t, ReviewResultVO vo, String rawAnswer) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.REVIEW);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion("预审作业票 " + t.getTicketNo() + " " + t.getTitle());
            row.setAnswer(rawAnswer != null ? rawAnswer : json.writeValueAsString(vo.getRuleFindings()));
            row.setSources(json.writeValueAsString(vo.getSources().stream().map(SourceVO::documentTitle).distinct().toList()));
            row.setHit(vo.getSources().isEmpty() ? 0 : 1);
            row.setModel(llm.chatEnabled() ? props.getChat().getModel() : "仅规则");
            row.setStatus(vo.getAiError() == null ? 1 : 0);
            row.setErrorMsg(vo.getAiError() == null ? null : vo.getAiError().substring(0, Math.min(500, vo.getAiError().length())));
            row.setCostMs(vo.getCostMs());
            chatLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存预审记录失败：{}", e.getMessage());
        }
    }

    private static Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "（未填写）" : s;
    }
}
