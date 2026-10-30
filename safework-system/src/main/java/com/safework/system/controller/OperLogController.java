package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.core.R;
import com.safework.system.dto.OperLogQueryDTO;
import com.safework.system.entity.SysOperLog;
import com.safework.system.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @SaCheckPermission("system:log:list")
    @Operation(summary = "操作日志（可按错误编号 traceId 查询）")
    @GetMapping("/page")
    public R<Page<SysOperLog>> page(OperLogQueryDTO query) {
        return R.ok(operLogService.page(query));
    }
}
