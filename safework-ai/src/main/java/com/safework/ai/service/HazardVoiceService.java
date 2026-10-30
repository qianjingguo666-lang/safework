package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.ai.asr.AsrException;
import com.safework.ai.asr.XfyunIatClient;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.hazard.HazardDraft;
import com.safework.ai.core.hazard.HazardDraftParser;
import com.safework.ai.core.hazard.HazardRules;
import com.safework.ai.core.hazard.SpaceRef;
import com.safework.ai.dto.ChatMessage;
import com.safework.ai.dto.HazardDraftVO;
import com.safework.ai.dto.TranscribeVO;
import com.safework.ai.entity.AiChatLog;
import com.safework.ai.mapper.AiChatLogMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.SpaceOptionVO;
import com.safework.safety.service.SpaceService;
import com.safework.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 语音智能上报隐患：
 * <pre>
 * 现场说一句话 ──讯飞语音听写──▶ 文字 ──大模型提取──▶ 标题 / 类别 / 等级 / 位置 / 受限空间 / 描述
 *                                          └─ 没配置大模型或调用失败：关键词规则兜底
 * 结果只用来“填表”，上报人核对、补充照片后自己点提交（人在回路），AI 不直接写入隐患台账。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HazardVoiceService {

    /** 最短 0.5 秒、最长 60 秒（讯飞语音听写单次最长 60 秒） */
    static final int MIN_BYTES = XfyunIatClient.BYTES_PER_SECOND / 2;
    static final int MAX_BYTES = XfyunIatClient.BYTES_PER_SECOND * 60;
    /** 发给大模型的受限空间最多多少个（控制 token） */
    static final int MAX_SPACES = 60;

    static final String SYSTEM_PROMPT = """
            你是化工企业的安全员助手，负责把一线员工口头描述的现场情况整理成隐患上报单。
            要求：
            1. title：一句话概括问题，不超过 30 字，例如“2# 储罐呼吸阀锈蚀卡涩”；
            2. category 只能是：设备设施、作业环境、人员行为、安全管理 之一；
            3. level 只能是 GENERAL（一般隐患）或 MAJOR（重大隐患）。可能直接导致火灾爆炸、中毒窒息、人员伤亡的判为 MAJOR，
               例如：易燃易爆或有毒介质泄漏、可燃/有毒气体报警器失效、受限空间未检测就进入、无票动火、安全设施被拆除；拿不准时判 MAJOR；
            4. levelReason：一句话说明定级理由，不超过 40 字；
            5. location：员工说到的位置，没说就返回空字符串，不要编造；
            6. spaceCode：如果描述的位置就是【受限空间台账】里的某一个，返回它的编号，否则返回 null；
            7. description：把口语整理成规范、简洁的书面描述（现象 + 可能后果），不超过 150 字，不要添加员工没说的事实。
            只输出 JSON，不要输出其他内容，格式：
            {"title":"","category":"","level":"GENERAL","levelReason":"","location":"","spaceCode":null,"description":""}""";

    private final AiProperties props;
    private final LlmClient llm;
    private final AiRateLimiter rateLimiter;
    private final SpaceService spaceService;
    private final AiChatLogMapper chatLogMapper;

    public boolean asrEnabled() {
        return props.getAsr().isEnabled();
    }

    /** 语音转文字：音频是前端录好的 16k 16bit 单声道 PCM */
    public TranscribeVO transcribe(byte[] pcm) {
        if (!asrEnabled()) {
            throw new BizException(400, "没有配置讯飞语音听写：请在 config/secret.yml 填写 safework.ai.asr 的 APPID、APIKey、APISecret，或者直接打字描述");
        }
        if (pcm == null || pcm.length < MIN_BYTES) {
            throw new BizException("录音太短，请按住说完一句话");
        }
        if (pcm.length > MAX_BYTES) {
            throw new BizException("录音不能超过 60 秒");
        }
        if (pcm.length % 2 != 0) {
            throw new BizException("音频格式不正确，需要 16k 16bit 单声道 PCM");
        }
        rateLimiter.check(StpUtil.getLoginIdAsLong());
        long start = System.currentTimeMillis();
        double seconds = Math.round(pcm.length * 10.0 / XfyunIatClient.BYTES_PER_SECOND) / 10.0;
        AiProperties.Asr c = props.getAsr();
        XfyunIatClient client = new XfyunIatClient(c.getHostUrl(), c.getAppId(), c.getApiKey(), c.getApiSecret(),
                c.getFrameBytes(), c.getFrameIntervalMs(), c.getTimeoutSeconds());
        String text = null;
        String error = null;
        try {
            text = client.recognize(pcm);
            if (text.isBlank()) {
                throw new BizException("没有听清，请靠近手机或电脑麦克风再说一遍");
            }
            return new TranscribeVO(text, seconds, System.currentTimeMillis() - start);
        } catch (AsrException e) {
            error = e.getMessage();
            log.warn("语音识别失败：{}", e.getMessage());
            throw new BizException(502, e.getMessage());
        } catch (RuntimeException e) {
            error = e.getMessage();
            throw e;
        } finally {
            saveLog(AiChatLog.ASR, "语音 " + seconds + " 秒", text, null, "iat", error, start);
        }
    }

    /** 一句话提取隐患草稿 */
    public HazardDraftVO parse(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            throw new BizException("请先说一句话或输入隐患情况");
        }
        long start = System.currentTimeMillis();
        List<SpaceOptionVO> options = spaceService.options();
        List<SpaceRef> spaces = options.stream().map(s -> new SpaceRef(s.getSpaceCode(), s.getSpaceName(), s.getLocation())).toList();

        HazardDraft draft;
        boolean aiUsed = false;
        List<String> notes = new ArrayList<>();
        String reply = null;
        String error = null;
        if (llm.chatEnabled()) {
            rateLimiter.check(StpUtil.getLoginIdAsLong());
            try {
                reply = llm.chat(List.of(ChatMessage.system(SYSTEM_PROMPT), ChatMessage.user(userPrompt(text, spaces))), 30);
                HazardDraft ai = HazardDraftParser.parse(reply);
                if (ai == null) {
                    throw new IllegalStateException("模型没有按格式返回 JSON");
                }
                draft = HazardRules.sanitize(ai, text, spaces);
                aiUsed = true;
            } catch (RuntimeException e) {
                error = e.getMessage();
                log.warn("隐患提取调用大模型失败，改用关键词规则：{}", e.getMessage());
                draft = HazardRules.extract(text, spaces);
                notes.add("大模型暂时不可用，已按关键词粗略填写，请仔细核对");
            }
        } else {
            draft = HazardRules.extract(text, spaces);
            notes.add("没有配置大模型，已按关键词粗略填写，请仔细核对");
        }
        notes.addAll(HazardRules.notes(draft));

        String code = draft.spaceCode();
        SpaceOptionVO space = code == null ? null : options.stream().filter(s -> code.equals(s.getSpaceCode())).findFirst().orElse(null);
        HazardDraftVO vo = new HazardDraftVO(draft.title(), draft.category(), draft.level(), draft.location(),
                space == null ? null : space.getId(), space == null ? null : space.getSpaceCode() + " " + space.getSpaceName(),
                draft.description(), draft.levelReason(), text, aiUsed, notes, System.currentTimeMillis() - start);
        saveLog(AiChatLog.HAZARD, text, reply, vo.title() + "｜" + vo.category() + "｜" + vo.level() + "｜" + vo.location(),
                aiUsed ? props.getChat().getModel() : "rule", error, start);
        return vo;
    }

    static String userPrompt(String text, List<SpaceRef> spaces) {
        String list = spaces.stream().limit(MAX_SPACES)
                .map(s -> s.code() + "｜" + s.name() + "｜" + (s.location() == null ? "" : s.location()))
                .collect(Collectors.joining("\n"));
        return "【受限空间台账】（编号｜名称｜位置）\n" + (list.isEmpty() ? "无" : list) + "\n\n【员工描述】\n" + text;
    }

    private void saveLog(String bizType, String question, String answer, String sources, String model, String error, long start) {
        try {
            AiChatLog row = new AiChatLog();
            row.setBizType(bizType);
            row.setUserId(StpUtil.getLoginIdAsLong());
            Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
            row.setUserName(nickname == null ? null : nickname.toString());
            row.setQuestion(question.length() > 1000 ? question.substring(0, 1000) : question);
            row.setAnswer(answer == null ? null : (answer.length() > 4000 ? answer.substring(0, 4000) : answer));
            row.setSources(sources);
            row.setHit(error == null && answer != null ? 1 : 0);
            row.setModel(model);
            row.setStatus(error == null ? 1 : 0);
            row.setErrorMsg(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
            row.setCostMs(System.currentTimeMillis() - start);
            chatLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("保存 AI 调用记录失败：{}", e.getMessage());
        }
    }
}
