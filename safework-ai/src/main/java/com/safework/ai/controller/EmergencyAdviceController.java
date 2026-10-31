package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.ai.dto.EmergencyAdviceVO;
import com.safework.ai.service.EmergencyAdviceService;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 应急助手", description = "根据现场情况匹配应急预案，从预案原文提炼立即处置要点")
@RestController
@RequestMapping("/ai/emergency")
@RequiredArgsConstructor
public class EmergencyAdviceController {

    private final EmergencyAdviceService service;

    @SaCheckPermission("emergency:event:list")
    @Operation(summary = "处置建议：建议启动哪个预案、现在立刻要做的事（只是建议，不会自动启动）")
    @PostMapping("/{eventId}/advise")
    public R<EmergencyAdviceVO> advise(@PathVariable Long eventId) {
        return R.ok(service.advise(eventId));
    }
}
