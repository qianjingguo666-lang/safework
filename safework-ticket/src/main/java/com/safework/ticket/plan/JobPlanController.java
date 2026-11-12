package com.safework.ticket.plan;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "作业计划管理")
@RestController
@RequestMapping("/ticket/plan")
@RequiredArgsConstructor
public class JobPlanController {
    private final JobPlanService service;

    @SaCheckPermission(JobPlanService.PERM_LIST)
    @GetMapping("/options")
    @Operation(summary = "作业类型与状态选项")
    public R<Map<String, Object>> options() {
        return R.ok(Map.of("statuses", JobPlanRules.STATUSES, "ticketTypes", JobPlanRules.TICKET_TYPES));
    }

    @SaCheckPermission(JobPlanService.PERM_LIST)
    @GetMapping("/page")
    @Operation(summary = "作业计划分页")
    public R<?> page(JobPlanDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(JobPlanService.PERM_LIST)
    @GetMapping("/{id}")
    public R<JobPlan> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "作业计划", action = "新建")
    @SaCheckPermission(JobPlanService.PERM_MANAGE)
    @PostMapping
    public R<Long> create(@Valid @RequestBody JobPlanDTOs.Save dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "作业计划", action = "修改")
    @SaCheckPermission(JobPlanService.PERM_MANAGE)
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody JobPlanDTOs.Save dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "作业计划", action = "开始作业")
    @SaCheckPermission(JobPlanService.PERM_MANAGE)
    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id) {
        service.action(id, "start");
        return R.ok();
    }

    @OperLog(module = "作业计划", action = "完成作业")
    @SaCheckPermission(JobPlanService.PERM_MANAGE)
    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        service.action(id, "complete");
        return R.ok();
    }

    @OperLog(module = "作业计划", action = "取消计划")
    @SaCheckPermission(JobPlanService.PERM_MANAGE)
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.action(id, "cancel");
        return R.ok();
    }
}