package com.safework.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.ai.client.LlmClient;
import com.safework.ai.config.AiProperties;
import com.safework.ai.core.TextChunker;
import com.safework.ai.core.VectorMath;
import com.safework.ai.entity.AiChunk;
import com.safework.ai.entity.AiDocument;
import com.safework.ai.mapper.AiChunkMapper;
import com.safework.ai.mapper.AiDocumentMapper;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库：上传 → 提取文字 → 切分 → 生成向量 → 入库。
 * <p>
 * 向量存在 MySQL 里，检索时在内存中计算相似度：一个企业的制度文件通常只有几百到几千个片段，
 * 全量计算只要几毫秒，不需要额外部署向量数据库。数据量上来后可以换成 pgvector / Milvus，
 * 只需要替换 {@link RetrievalService} 的实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    /** 内存索引中的一个片段 */
    public record IndexedChunk(Long id, Long documentId, String documentTitle, Integer pageNo, String content,
                               float[] vector) {
    }

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private final AiDocumentMapper documentMapper;
    private final AiChunkMapper chunkMapper;
    private final DocumentParser parser;
    private final LlmClient llm;
    private final AiProperties props;
    private final UserService userService;
    private final TransactionTemplate tx;

    /** 每个企业一份索引缓存；上传、删除、生成向量后失效 */
    private final Map<Long, List<IndexedChunk>> indexCache = new ConcurrentHashMap<>();

    public Page<AiDocument> page(long current, long size, String keyword) {
        return documentMapper.selectPage(new Page<>(current, size), Wrappers.<AiDocument>lambdaQuery()
                .like(StringUtils.hasText(keyword), AiDocument::getTitle, keyword)
                .orderByDesc(AiDocument::getCreateTime));
    }

    public List<AiChunk> chunks(Long documentId, int limit) {
        getById(documentId);
        return chunkMapper.selectList(Wrappers.<AiChunk>lambdaQuery()
                .eq(AiChunk::getDocumentId, documentId)
                .orderByAsc(AiChunk::getChunkIndex)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public long documentCount() {
        return documentMapper.selectCount(Wrappers.<AiDocument>lambdaQuery().eq(AiDocument::getStatus, AiDocument.READY));
    }

    public long chunkCount() {
        return index().size();
    }

    public AiDocument upload(MultipartFile file, String title) {
        String fileName = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
        String ext = DocumentParser.extension(fileName);
        if (!DocumentParser.SUPPORTED.contains(ext)) {
            throw new BizException("只支持 PDF、TXT、MD 文件");
        }
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件为空或超过 20MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }

        // 1. 提取文字、切分（解析失败直接报错，不入库）
        List<TextChunker.PageText> pages = parser.parse(fileName, bytes);
        int charCount = pages.stream().mapToInt(p -> p.text() == null ? 0 : p.text().length()).sum();
        List<TextChunker.Chunk> chunks = new TextChunker(props.getRag().getChunkSize(), props.getRag().getChunkOverlap())
                .split(pages);
        if (chunks.isEmpty()) {
            throw new BizException("文件中没有可用的文字内容");
        }

        // 2. 文档和片段在一个事务里入库（不包含调用外部接口，事务要短）
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        AiDocument doc = new AiDocument();
        doc.setTitle(StringUtils.hasText(title) ? title.trim() : fileName.replaceFirst("\\.[^.]+$", ""));
        doc.setFileName(fileName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setCharCount(charCount);
        doc.setChunkCount(chunks.size());
        doc.setEmbedded(0);
        doc.setStatus(AiDocument.READY);
        doc.setUploaderId(me.getId());
        doc.setUploaderName(me.getNickname());
        tx.executeWithoutResult(s -> {
            documentMapper.insert(doc);
            for (TextChunker.Chunk c : chunks) {
                AiChunk row = new AiChunk();
                row.setDocumentId(doc.getId());
                row.setChunkIndex(c.index());
                row.setPageNo(c.pageNo());
                row.setContent(c.content());
                chunkMapper.insert(row);
            }
        });

        // 3. 生成向量：失败不影响使用（退化为关键词检索），记录原因，之后可以重试
        if (llm.embeddingEnabled()) {
            try {
                embed(doc.getId());
                doc.setEmbedded(1);
            } catch (Exception e) {
                log.warn("文档 {} 生成向量失败", doc.getId(), e);
                doc.setErrorMsg("向量生成失败，暂时使用关键词检索：" + e.getMessage());
                AiDocument patch = new AiDocument();
                patch.setId(doc.getId());
                patch.setErrorMsg(truncate(doc.getErrorMsg(), 500));
                documentMapper.updateById(patch);
            }
        }
        invalidate();
        return doc;
    }

    /** 为文档的所有片段生成向量（也用于配置了向量模型后，给旧文档补生成） */
    public void embed(Long documentId) {
        if (!llm.embeddingEnabled()) {
            throw new BizException("未配置向量模型，请在 config/secret.yml 中填写 embedding.api-key");
        }
        getById(documentId);
        List<AiChunk> rows = chunkMapper.selectList(Wrappers.<AiChunk>lambdaQuery()
                .eq(AiChunk::getDocumentId, documentId)
                .orderByAsc(AiChunk::getChunkIndex));
        List<float[]> vectors = llm.embed(rows.stream().map(AiChunk::getContent).toList());
        for (int i = 0; i < rows.size(); i++) {
            AiChunk patch = new AiChunk();
            patch.setId(rows.get(i).getId());
            patch.setEmbedding(VectorMath.encode(vectors.get(i)));
            chunkMapper.updateById(patch);
        }
        AiDocument patch = new AiDocument();
        patch.setId(documentId);
        patch.setEmbedded(1);
        patch.setErrorMsg("");
        documentMapper.updateById(patch);
        invalidate();
    }

    public void remove(Long documentId) {
        getById(documentId);
        tx.executeWithoutResult(s -> {
            chunkMapper.delete(Wrappers.<AiChunk>lambdaQuery().eq(AiChunk::getDocumentId, documentId));
            documentMapper.deleteById(documentId);
        });
        invalidate();
    }

    /** 当前企业的检索索引（带缓存） */
    public List<IndexedChunk> index() {
        Long tenantId = TenantContext.get();
        return indexCache.computeIfAbsent(tenantId, id -> loadIndex());
    }

    private List<IndexedChunk> loadIndex() {
        Map<Long, AiDocument> docs = documentMapper.selectList(Wrappers.<AiDocument>lambdaQuery()
                        .eq(AiDocument::getStatus, AiDocument.READY))
                .stream().collect(Collectors.toMap(AiDocument::getId, Function.identity()));
        if (docs.isEmpty()) {
            return List.of();
        }
        return chunkMapper.selectList(Wrappers.<AiChunk>lambdaQuery()
                        .in(AiChunk::getDocumentId, docs.keySet())
                        .orderByAsc(AiChunk::getDocumentId).orderByAsc(AiChunk::getChunkIndex))
                .stream()
                .map(c -> new IndexedChunk(c.getId(), c.getDocumentId(), docs.get(c.getDocumentId()).getTitle(),
                        c.getPageNo(), c.getContent(), VectorMath.decode(c.getEmbedding())))
                .toList();
    }

    private void invalidate() {
        indexCache.remove(TenantContext.get());
    }

    public AiDocument getById(Long id) {
        AiDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(404, "文档不存在");
        }
        return doc;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
