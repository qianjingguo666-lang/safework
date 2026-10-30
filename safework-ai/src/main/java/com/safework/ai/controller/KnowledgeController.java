package com.safework.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.ai.entity.AiChunk;
import com.safework.ai.entity.AiDocument;
import com.safework.ai.service.KnowledgeService;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "AI 知识库", description = "上传制度文件、切分、生成向量")
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @SaCheckPermission("ai:knowledge:list")
    @Operation(summary = "文档列表")
    @GetMapping("/page")
    public R<Page<AiDocument>> page(@RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        return R.ok(knowledgeService.page(current, size, keyword));
    }

    /** 预览文档被切成了哪些片段，方便排查“为什么检索不到” */
    @SaCheckPermission("ai:knowledge:list")
    @Operation(summary = "预览文档被切成了哪些片段，方便排查“为什么检索不到”")
    @GetMapping("/{id}/chunks")
    public R<List<AiChunk>> chunks(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        return R.ok(knowledgeService.chunks(id, limit));
    }

    @OperLog(module = "AI 知识库", action = "上传文档", saveParams = false)
    @SaCheckPermission("ai:knowledge:upload")
    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public R<AiDocument> upload(@RequestPart("file") MultipartFile file,
                                @RequestParam(required = false) String title) {
        return R.ok(knowledgeService.upload(file, title));
    }

    @OperLog(module = "AI 知识库", action = "生成向量")
    @SaCheckPermission("ai:knowledge:upload")
    @Operation(summary = "生成向量")
    @PostMapping("/{id}/embed")
    public R<Void> embed(@PathVariable Long id) {
        knowledgeService.embed(id);
        return R.ok();
    }

    @OperLog(module = "AI 知识库", action = "删除文档")
    @SaCheckPermission("ai:knowledge:remove")
    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        knowledgeService.remove(id);
        return R.ok();
    }
}
