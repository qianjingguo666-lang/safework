package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.ai.dto.AiQuestionDTO;
import com.safework.ai.dto.AiQuestionResultVO;
import com.safework.ai.service.AiQuestionService;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 出题", description = "根据知识库里的制度文件生成考试题，校验去重后以待审核状态入库")
@RestController
@RequestMapping("/ai/question")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionService service;

    @OperLog(module = "培训题库", action = "AI 出题")
    @SaCheckPermission("training:question:ai")
    @Operation(summary = "AI 出题（同步，约 20~60 秒）")
    @PostMapping("/generate")
    public R<AiQuestionResultVO> generate(@Valid @RequestBody AiQuestionDTO dto) {
        return R.ok(service.generate(dto));
    }
}
