package com.safework.ticket.kpi;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "安全目标考核", description = "年度安全目标、各模块自动统计实际值、红黄绿亮灯、得分、按月趋势")
@RestController
@RequestMapping("/safety/kpi")
@RequiredArgsConstructor
public class KpiController {

    private final KpiService service;

    @SaCheckPermission(KpiService.PERM_LIST)
    @Operation(summary = "年度安全目标完成情况（年初至今 + 每月趋势），不传年度为今年")
    @GetMapping("/report")
    public R<KpiVOs.Report> report(@RequestParam(required = false) Integer year) {
        return R.ok(service.report(year == null ? LocalDate.now().getYear() : year));
    }

    @SaCheckPermission(KpiService.PERM_LIST)
    @Operation(summary = "某年的目标值和权重（没设置过返回默认值）")
    @GetMapping("/targets")
    public R<List<KpiVOs.TargetRow>> targets(@RequestParam Integer year) {
        return R.ok(service.targets(year));
    }

    @OperLog(module = "安全目标考核", action = "设置年度目标")
    @SaCheckPermission(KpiService.PERM_EDIT)
    @Operation(summary = "设置年度目标（权重合计 100）")
    @PutMapping("/targets")
    public R<Void> saveTargets(@Valid @RequestBody KpiDTOs.Targets dto) {
        service.saveTargets(dto);
        return R.ok();
    }
}
