package com.safework.ticket.handover;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/ticket/handover")
@SaCheckPermission("ticket:list")
@RequiredArgsConstructor
public class HandoverController {
    private final HandoverService service;

    public record Create(@NotNull Long ticketId, @NotNull Long receiverId,
                         @NotBlank @Size(max=500) String siteCondition,
                         @NotBlank @Size(max=500) String unfinished,
                         @NotBlank @Size(max=500) String recheck) {}
    public record Accept(@NotNull @Size(min=3, max=3) List<String> checked) {}

    @GetMapping("/page")
    public R<Page<ShiftHandover>> page(@RequestParam(defaultValue="1") long current,
            @RequestParam(defaultValue="false") boolean awaitingMe, @RequestParam(required=false) Long recordId) {
        return R.ok(service.page(current, awaitingMe, recordId));
    }

    @PostMapping
    @OperLog(module="作业交接班", action="交班")
    public R<Long> create(@Valid @RequestBody Create form) {
        return R.ok(service.create(form));
    }

    @PostMapping("/{id}/accept")
    @OperLog(module="作业交接班", action="确认接班")
    public R<Void> accept(@PathVariable Long id, @Valid @RequestBody Accept form) {
        service.accept(id, form.checked());
        return R.ok();
    }
}
