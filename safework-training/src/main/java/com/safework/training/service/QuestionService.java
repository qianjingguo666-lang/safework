package com.safework.training.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.training.core.PaperBuilder;
import com.safework.training.core.QuestionChecker;
import com.safework.training.core.QuestionDraft;
import com.safework.training.dto.ImportResult;
import com.safework.training.dto.QuestionQueryDTO;
import com.safework.training.dto.QuestionSaveDTO;
import com.safework.training.dto.QuestionVO;
import com.safework.training.entity.TrainQuestion;
import com.safework.training.enums.QuestionType;
import com.safework.training.mapper.TrainQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 题库 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    public static final int PENDING = 0;
    public static final int ENABLED = 1;
    public static final int DISABLED = 2;
    public static final String MANUAL = "MANUAL";
    public static final String AI = "AI";

    private final TrainQuestionMapper questionMapper;
    private final ObjectMapper json = new ObjectMapper();

    public IPage<QuestionVO> page(QuestionQueryDTO q) {
        Page<TrainQuestion> page = questionMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()),
                Wrappers.<TrainQuestion>lambdaQuery()
                        .eq(q.getType() != null, TrainQuestion::getType, q.getType())
                        .eq(StringUtils.hasText(q.getCategory()), TrainQuestion::getCategory, q.getCategory())
                        .eq(q.getStatus() != null, TrainQuestion::getStatus, q.getStatus())
                        .eq(StringUtils.hasText(q.getSource()), TrainQuestion::getSource, q.getSource())
                        .like(StringUtils.hasText(q.getKeyword()), TrainQuestion::getContent, q.getKeyword())
                        .orderByAsc(TrainQuestion::getStatus)
                        .orderByDesc(TrainQuestion::getCreateTime));
        return page.convert(this::toVO);
    }

    /** 已有的分类（下拉提示用） */
    public List<String> categories() {
        return questionMapper.selectObjs(new QueryWrapper<TrainQuestion>()
                        .select("DISTINCT category").orderByAsc("category"))
                .stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    /** 每种题型启用的题目数量（建考试时提示“题库够不够”） */
    public Map<QuestionType, Long> countEnabled(String category) {
        Map<QuestionType, Long> result = new EnumMap<>(QuestionType.class);
        for (QuestionType t : QuestionType.values()) {
            result.put(t, 0L);
        }
        for (Map<String, Object> row : questionMapper.selectMaps(new QueryWrapper<TrainQuestion>()
                .select("type AS t", "COUNT(*) AS c")
                .eq("status", ENABLED)
                .eq(StringUtils.hasText(category), "category", category)
                .groupBy("type"))) {
            result.put(QuestionType.valueOf(String.valueOf(row.get("t"))), ((Number) row.get("c")).longValue());
        }
        return result;
    }

    /** 组卷用：按题型分组的启用题目 */
    public Map<QuestionType, List<PaperBuilder.Source>> enabledByType(String category) {
        Map<QuestionType, List<PaperBuilder.Source>> pools = new EnumMap<>(QuestionType.class);
        for (TrainQuestion q : questionMapper.selectList(Wrappers.<TrainQuestion>lambdaQuery()
                .eq(TrainQuestion::getStatus, ENABLED)
                .eq(StringUtils.hasText(category), TrainQuestion::getCategory, category))) {
            pools.computeIfAbsent(q.getType(), k -> new ArrayList<>())
                    .add(new PaperBuilder.Source(q.getId(), q.getType(), q.getContent(), parseOptions(q.getOptionList()),
                            q.getAnswer(), q.getAnalysis()));
        }
        return pools;
    }

    public Long create(QuestionSaveDTO dto) {
        TrainQuestion q = new TrainQuestion();
        fill(q, dto);
        q.setSource(MANUAL);
        q.setStatus(ENABLED);
        questionMapper.insert(q);
        return q.getId();
    }

    public void update(Long id, QuestionSaveDTO dto) {
        TrainQuestion q = load(id);
        fill(q, dto);
        questionMapper.updateById(q);
    }

    /** 审核通过：只处理待审核的题 */
    public int approve(List<Long> ids) {
        TrainQuestion change = new TrainQuestion();
        change.setStatus(ENABLED);
        return questionMapper.update(change, Wrappers.<TrainQuestion>lambdaUpdate()
                .in(TrainQuestion::getId, ids)
                .eq(TrainQuestion::getStatus, PENDING));
    }

    /** 启用 / 停用。已经生成的试卷是快照，停用题目不影响正在进行的考试 */
    public void changeStatus(Long id, Integer status) {
        if (!Objects.equals(status, ENABLED) && !Objects.equals(status, DISABLED)) {
            throw new BizException("状态只能是启用或停用");
        }
        TrainQuestion q = load(id);
        q.setStatus(status);
        questionMapper.updateById(q);
    }

    public int remove(List<Long> ids) {
        return questionMapper.deleteBatchIds(ids);
    }

    /**
     * 批量入库（AI 出题用）：逐题校验格式，和题库已有题目、本批其他题目比较去重，
     * 通过的以“待审核”状态保存——AI 生成的题必须由人确认后才能用于考试。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importDrafts(List<QuestionDraft> drafts, String category, String sourceDoc) {
        Set<String> seen = new HashSet<>();
        List<String> contents = drafts.stream().map(d -> d.content() == null ? "" : d.content().trim())
                .filter(s -> !s.isEmpty()).toList();
        if (!contents.isEmpty()) {
            questionMapper.selectObjs(new QueryWrapper<TrainQuestion>().select("content").in("content", contents))
                    .forEach(c -> seen.add(String.valueOf(c).trim()));
        }
        int saved = 0;
        List<ImportResult.Skipped> skipped = new ArrayList<>();
        for (QuestionDraft d : drafts) {
            List<String> options = d.type() == QuestionType.JUDGE ? QuestionChecker.JUDGE_OPTIONS : trim(d.options());
            String content = d.content() == null ? null : d.content().trim();
            String error = QuestionChecker.check(d.type(), content, options, d.answer());
            if (error == null && !seen.add(content)) {
                error = "和已有题目重复";
            }
            if (error != null) {
                skipped.add(new ImportResult.Skipped(content == null ? "（空）" : content, error));
                continue;
            }
            TrainQuestion q = new TrainQuestion();
            q.setType(d.type());
            q.setCategory(category);
            q.setContent(content);
            q.setOptionList(writeOptions(options));
            q.setAnswer(QuestionChecker.normalizeAnswer(d.answer()));
            String analysis = d.analysis() == null ? null : d.analysis().trim();
            q.setAnalysis(analysis != null && analysis.length() > 500 ? analysis.substring(0, 500) : analysis);
            q.setSource(AI);
            q.setSourceDoc(sourceDoc);
            q.setStatus(PENDING);
            questionMapper.insert(q);
            saved++;
        }
        return new ImportResult(saved, skipped);
    }

    private void fill(TrainQuestion q, QuestionSaveDTO dto) {
        List<String> options = dto.getType() == QuestionType.JUDGE ? QuestionChecker.JUDGE_OPTIONS : trim(dto.getOptions());
        String content = dto.getContent().trim();
        String error = QuestionChecker.check(dto.getType(), content, options, dto.getAnswer());
        if (error != null) {
            throw new BizException(error);
        }
        q.setType(dto.getType());
        q.setCategory(dto.getCategory().trim());
        q.setContent(content);
        q.setOptionList(writeOptions(options));
        q.setAnswer(QuestionChecker.normalizeAnswer(dto.getAnswer()));
        q.setAnalysis(StringUtils.hasText(dto.getAnalysis()) ? dto.getAnalysis().trim() : null);
    }

    private TrainQuestion load(Long id) {
        TrainQuestion q = questionMapper.selectById(id);
        if (q == null) {
            throw new BizException(404, "题目不存在");
        }
        return q;
    }

    QuestionVO toVO(TrainQuestion q) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setType(q.getType());
        vo.setTypeLabel(q.getType() == null ? null : q.getType().getLabel());
        vo.setCategory(q.getCategory());
        vo.setContent(q.getContent());
        vo.setOptions(parseOptions(q.getOptionList()));
        vo.setAnswer(q.getAnswer());
        vo.setAnalysis(q.getAnalysis());
        vo.setSource(q.getSource());
        vo.setSourceDoc(q.getSourceDoc());
        vo.setStatus(q.getStatus());
        vo.setCreateTime(q.getCreateTime());
        return vo;
    }

    List<String> parseOptions(String s) {
        if (!StringUtils.hasText(s)) {
            return List.of();
        }
        try {
            return json.readValue(s, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeOptions(List<String> options) {
        try {
            return json.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> trim(List<String> options) {
        return options == null ? null : options.stream().map(o -> o == null ? null : o.trim()).toList();
    }
}
