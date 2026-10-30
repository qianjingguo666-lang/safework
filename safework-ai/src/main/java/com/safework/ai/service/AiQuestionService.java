package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.AiQuestionParser;
import com.safework.ai.core.ExcerptPicker;
import com.safework.ai.dto.AiQuestionDTO;
import com.safework.ai.dto.AiQuestionResultVO;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.entity.AiChunk;
import com.safework.ai.entity.AiDocument;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.common.exception.BizException;
import com.safework.system.service.AuthService;
import com.safework.training.core.QuestionDraft;
import com.safework.training.dto.ImportResult;
import com.safework.training.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 出题：从知识库的制度文件里选取原文 → 让大模型按固定 JSON 格式出题 → 解析、逐题校验、去重 →
 * 以“待审核”状态入库。AI 生成的题必须由安全员审核通过后才能用于考试（人在回路）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionService {

    /** 发给模型的原文上限（字），控制 token 成本 */
    static final int MAX_EXCERPT = 6000;

    static final String SYSTEM_PROMPT = """
            你是企业安全培训的出题老师，负责根据公司的安全制度原文出考试题。
            要求：
            1. 只能依据【制度原文】出题，题目的答案必须能在原文中找到依据，不要使用原文以外的知识，不要编造数值；
            2. 题目要考查关键要求（数值、时间、禁止事项、必须做的措施），不要考文件标题、章节编号这类无意义的内容；
            3. 单选题 4 个选项，只有 1 个正确；多选题 4 个选项，2~3 个正确；判断题答案是“正确”或“错误”；
            4. 干扰项要似是而非，不能一眼看出是错的；题目之间不要重复；
            5. 题干里不要出现“根据原文”“根据制度”这类字样；选项内容不要带 A、B、C、D 前缀；
            6. analysis 写明依据，引用原文要点，不超过 80 字。
            只输出 JSON，不要输出其他内容，格式：
            {"questions":[{"type":"SINGLE","content":"题干","options":["选项1","选项2","选项3","选项4"],"answer":"B","analysis":"依据……"},
            {"type":"MULTI","content":"题干","options":["…","…","…","…"],"answer":"AC","analysis":"…"},
            {"type":"JUDGE","content":"题干","options":["正确","错误"],"answer":"B","analysis":"…"}]}""";

    private final LlmClient llm;
    private final AiProperties props;
    private final AiRateLimiter rateLimiter;
    private final KnowledgeService knowledgeService;
    private final QuestionService questionService;
    private final AiChatLogMapper chatLogMapper;

    public AiQuestionResultVO generate(AiQuestionDTO dto) {
        long start = System.currentTimeMillis();
        int total = dto.getSingleCount() + dto.getMultiCount() + dto.getJudgeCount();
        if (total == 0) {
            throw new BizException("至少出 1 道题");
        }
        if (total > 15) {
            throw new BizException("一次最多出 15 道题，题目太多时模型容易出错或超时");
        }
        if (!llm.chatEnabled()) {
            throw new BizException(400, "AI 出题需要配置大模型：请在 config/secret.yml 配置对话模型");
        }
        rateLimiter.check(StpUtil.getLoginIdAsLong());

        AiDocument doc = knowledgeService.getById(dto.getDocumentId());
        List<String> chunks = knowledgeService.chunks(dto.getDocumentId(), 200).stream().map(AiChunk::getContent).toList();
        if (chunks.isEmpty()) {
            throw new BizException("这个文件没有可用的文字内容");
        }
        String excerpt = ExcerptPicker.pick(chunks, dto.getTopic(), MAX_EXCERPT);

        String user = "【制度原文】《" + doc.getTitle() + "》\n" + excerpt + "\n\n【出题要求】\n"
                + "单选题 " + dto.getSingleCount() + " 道，多选题 " + dto.getMultiCount() + " 道，判断题 " + dto.getJudgeCount() + " 道。"
                + (dto.getTopic() == null || dto.getTopic().isBlank() ? "" : "重点考查：" + dto.getTopic().trim() + "。")
                + "判断题里正确和错误的题都要有。";
        String reply = null;
        String error = null;
        AiQuestionResultVO result = null;
        try {
            reply = llm.chat(List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(user)), 120);
            List<QuestionDraft> drafts = AiQuestionParser.parse(reply);
            if (drafts.isEmpty()) {
                throw new BizException(502, "没有从模型的回复中解析出题目，请重试");
            }
            ImportResult r = questionService.importDrafts(drafts, dto.getCategory().trim(), doc.getTitle());
            result = new AiQuestionResultVO(doc.getTitle(), drafts.size(), r.saved(), r.skipped(), System.currentTimeMillis() - start);
            return result;
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            saveLog(doc.getTitle(), user.length(), reply, result, error, start);
        }
    }

    private void saveLog(String docTitle, int promptChars, String reply, AiQuestionResultVO result, String error, long start) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(AiChatLog.QUESTION);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion("AI 出题：《" + docTitle + "》（原文 " + promptChars + " 字）");
            row.setAnswer(reply == null ? null : (reply.length() > 4000 ? reply.substring(0, 4000) : reply));
            row.setSources(result == null ? null
                    : "生成 " + result.generated() + " 道，入库 " + result.saved() + " 道，跳过 " + result.skipped().size() + " 道");
            row.setHit(result != null && result.saved() > 0 ? 1 : 0);
            row.setModel(props.getChat().getModel());
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存 AI 出题记录失败：{}", e.getMessage());
        }
    }
}
