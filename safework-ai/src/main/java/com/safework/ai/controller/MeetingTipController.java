package com.safework.ai.controller;

import com.safework.ai.service.MeetingTipService;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 班前会助手", description = "根据今天的作业票、重点风险点、未整改隐患生成班前会安全要点")
@RestController
@RequestMapping("/ai/meeting")
@RequiredArgsConstructor
public class MeetingTipController {

    private final MeetingTipService service;

    @Operation(summary = "生成班前会安全要点（班组长或安全管理员；只是草稿，可以修改后再保存）")
    @PostMapping("/{teamId}/tips")
    public R<MeetingTipService.Tips> tips(@PathVariable Long teamId) {
        return R.ok(service.generate(teamId));
    }
}
