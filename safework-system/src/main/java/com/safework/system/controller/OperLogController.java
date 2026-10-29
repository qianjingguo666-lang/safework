package com.safework.system.controller;

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

@RestController
@RequestMapping("/system/log")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @SaCheckPermission("system:log:list")
    @GetMapping("/page")
    public R<Page<SysOperLog>> page(OperLogQueryDTO query) {
        return R.ok(operLogService.page(query));
    }
}
