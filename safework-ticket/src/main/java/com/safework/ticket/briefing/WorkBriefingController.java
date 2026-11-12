package com.safework.ticket.briefing;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "作业安全交底")
@RestController
@RequestMapping("/ticket/briefing")
@RequiredArgsConstructor
public class WorkBriefingController {
    private final WorkBriefingService service;

    @SaCheckPermission(WorkBriefingService.PERM_LIST)
    @GetMapping("/page")
    @Operation(summary = "安全交底分页，参与人只看自己的交底")
    public R<Page<WorkBriefingVOs.Row>> page(WorkBriefingDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(WorkBriefingService.PERM_LIST)
    @GetMapping("/by-ticket/{ticketId}")
    public R<WorkBriefingVOs.Detail> byTicket(@PathVariable Long ticketId) {
        return R.ok(service.byTicket(ticketId));
    }

    @SaCheckPermission(WorkBriefingService.PERM_LIST)
    @GetMapping("/{id}")
    public R<WorkBriefingVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "作业安全交底", action = "创建交底")
    @SaCheckPermission(WorkBriefingService.PERM_MANAGE)
    @PostMapping
    public R<Long> create(@Valid @RequestBody WorkBriefingDTOs.Create dto) {
        return R.ok(service.create(dto));
    }

    @OperLog(module = "作业安全交底", action = "签字确认")
    @SaCheckPermission(WorkBriefingService.PERM_SIGN)
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @Valid @RequestBody WorkBriefingDTOs.Confirm dto) {
        service.confirm(id, dto.getSignature());
        return R.ok();
    }
}