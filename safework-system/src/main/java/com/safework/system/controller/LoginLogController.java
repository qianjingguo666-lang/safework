package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.core.R;
import com.safework.system.dto.LoginLogQueryDTO;
import com.safework.system.entity.SysLoginLog;
import com.safework.system.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 企业管理员查看本企业的登录日志 */
@Tag(name = "登录日志")
@RestController
@RequestMapping("/system/loginlog")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @SaCheckPermission("system:loginlog:list")
    @Operation(summary = "本企业登录日志")
    @GetMapping("/page")
    public R<Page<SysLoginLog>> page(LoginLogQueryDTO query) {
        query.setTenantCode(null);
        return R.ok(loginLogService.page(query));
    }
}
