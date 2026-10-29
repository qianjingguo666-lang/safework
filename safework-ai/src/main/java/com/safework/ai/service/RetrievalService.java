package com.safework.ai.service;

import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.KeywordScorer;
import com.safework.ai.core.VectorMath;
import com.safework.ai.dto.SourceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 检索：混合检索 = 0.7 × 向量相似度 + 0.3 × 关键词得分。
 * - 向量检索擅长“意思相近、说法不同”（问“进罐前要做什么”能找到“作业前必须先通风再检测”）
 * - 关键词检索擅长编号、数值、专有名词
 * 没有配置向量模型、或文档还没生成向量时，自动退化为纯关键词检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final double VECTOR_WEIGHT = 0.7;

    private final KnowledgeService knowledge;
    private final LlmClient llm;
    private final AiProperties props;

    public List<SourceVO> retrieve(String query, int topK) {
        List<KnowledgeService.IndexedChunk> index = knowledge.index();
        if (index.isEmpty()) {
            return List.of();
        }
        Set<String> qTokens = KeywordScorer.tokens(query);
        float[] qVec = null;
        boolean anyVector = index.stream().anyMatch(c -> c.vector() != null);
        if (anyVector && llm.embeddingEnabled()) {
            try {
                qVec = llm.embedOne(query);
            } catch (Exception e) {
                // 向量服务挂了也不能影响问答，降级为关键词检索
                log.warn("问题向量化失败，降级为关键词检索：{}", e.getMessage());
            }
        }
        AiProperties.Rag rag = props.getRag();
        record Scored(KnowledgeService.IndexedChunk chunk, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeService.IndexedChunk c : index) {
            double kw = KeywordScorer.score(qTokens, c.content());
            double score;
            double threshold;
            if (qVec != null && c.vector() != null) {
                score = VECTOR_WEIGHT * VectorMath.cosine(qVec, c.vector()) + (1 - VECTOR_WEIGHT) * kw;
                threshold = rag.getMinHybridScore();
            } else {
                score = kw;
                threshold = rag.getMinScore();
            }
            if (score >= threshold) {
                scored.add(new Scored(c, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<SourceVO> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            KnowledgeService.IndexedChunk c = scored.get(i).chunk();
            result.add(new SourceVO(i + 1, String.valueOf(c.documentId()), c.documentTitle(), c.pageNo(), c.content(),
                    Math.round(scored.get(i).score() * 1000) / 1000.0));
        }
        return result;
    }

    /** 把检索结果拼成提示词里的“参考资料”部分 */
    public static String formatContext(List<SourceVO> sources) {
        StringBuilder sb = new StringBuilder();
        for (SourceVO s : sources) {
            sb.append('[').append(s.index()).append("] 《").append(s.documentTitle()).append('》');
            if (s.pageNo() != null) {
                sb.append(" 第").append(s.pageNo()).append("页");
            }
            sb.append('\n').append(s.content()).append("\n\n");
        }
        return sb.toString();
    }
}
