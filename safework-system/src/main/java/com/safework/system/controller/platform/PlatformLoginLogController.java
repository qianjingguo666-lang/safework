package com.safework.system.controller.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safework.common.core.R;
import com.safework.system.dto.LoginLogQueryDTO;
import com.safework.system.entity.SysLoginLog;
import com.safework.system.service.LoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台查看全部企业的登录日志（排查“我登不上去”、发现撞库攻击） */
@Tag(name = "平台-登录日志")
@RestController
@RequestMapping("/platform/loginlog")
@RequiredArgsConstructor
public class PlatformLoginLogController {

    private final LoginLogService loginLogService;

    @SaCheckPermission("platform:loginlog:list")
    @Operation(summary = "全部企业的登录日志")
    @GetMapping("/page")
    public R<IPage<SysLoginLog>> page(LoginLogQueryDTO query) {
        return R.ok(loginLogService.pageAllTenants(query));
    }
}
