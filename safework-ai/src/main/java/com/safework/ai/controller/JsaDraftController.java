package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.ai.service.JsaDraftService;
import com.safework.common.core.R;
import com.safework.ticket.jsa.JsaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 起草 JSA", description = "根据作业信息拆步骤、识别危害、评估风险、写控制措施；没配置大模型时按模板起草")
@RestController
@RequestMapping("/ai/jsa")
@RequiredArgsConstructor
public class JsaDraftController {

    private final JsaDraftService service;

    @Data
    public static class DraftRequest {
        private String ticketType;
        private String title;
        private String workContent;
        private String location;
    }

    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "起草 JSA 步骤（只是草稿，不保存）")
    @PostMapping("/draft")
    public R<JsaDraftService.Result> draft(@RequestBody DraftRequest req) {
        return R.ok(service.draft(req.getTicketType(), req.getTitle(), req.getWorkContent(), req.getLocation()));
    }
}
