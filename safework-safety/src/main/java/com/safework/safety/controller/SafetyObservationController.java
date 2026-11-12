package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.entity.SafetyObservation;
import com.safework.safety.observation.SafetyObservationDTOs;
import com.safework.safety.observation.SafetyObservationDetailVO;
import com.safework.safety.observation.SafetyObservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "未遂事件与安全观察", description = "现场报告、原因分析、改进措施和隐患闭环")
@RestController
@RequestMapping("/safety/observation")
@RequiredArgsConstructor
public class SafetyObservationController {
    private final SafetyObservationService service;

    @SaCheckPermission(SafetyObservationService.PERM_LIST)
    @Operation(summary = "分页查询：安全员看全部，其他人查看本人上报记录")
    @GetMapping("/page")
    public R<Page<SafetyObservation>> page(SafetyObservationDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "现场上报未遂事件或安全观察")
    @OperLog(module = "未遂事件与安全观察", action = "上报")
    @PostMapping
    public R<Long> report(@Valid @RequestBody SafetyObservationDTOs.Report dto) {
        return R.ok(service.report(dto));
    }

    @SaCheckPermission(SafetyObservationService.PERM_LIST)
    @Operation(summary = "记录详情")
    @GetMapping("/{id}")
    public R<SafetyObservationDetailVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @SaCheckPermission(SafetyObservationService.PERM_MANAGE)
    @OperLog(module = "未遂事件与安全观察", action = "原因分析与改进措施")
    @Operation(summary = "保存原因分析和改进措施；可同步转为隐患闭环")
    @PutMapping("/{id}/analysis")
    public R<Void> analysis(@PathVariable Long id, @Valid @RequestBody SafetyObservationDTOs.Analysis dto) {
        service.analyze(id, dto);
        return R.ok();
    }

    @SaCheckPermission(SafetyObservationService.PERM_MANAGE)
    @OperLog(module = "未遂事件与安全观察", action = "关闭")
    @Operation(summary = "关闭观察；转成的隐患须先验收完成")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        service.close(id);
        return R.ok();
    }
}
