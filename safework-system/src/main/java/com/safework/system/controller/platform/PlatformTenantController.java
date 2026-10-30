package com.safework.system.controller.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.StatusDTO;
import com.safework.system.dto.platform.AdminPasswordDTO;
import com.safework.system.dto.platform.PlatformStatsVO;
import com.safework.system.dto.platform.TenantCreateDTO;
import com.safework.system.dto.platform.TenantQueryDTO;
import com.safework.system.dto.platform.TenantUpdateDTO;
import com.safework.system.dto.platform.TenantVO;
import com.safework.system.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台端：企业管理。/platform/** 只有运营平台（租户 0）能访问，拦截器里还有一道检查（见 TenantInterceptor）。
 */
@Tag(name = "平台-企业管理", description = "开通、修改、续费、停用企业")
@RestController
@RequestMapping("/platform/tenant")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantService tenantService;

    @SaCheckPermission("platform:tenant:list")
    @Operation(summary = "企业列表")
    @GetMapping("/page")
    public R<IPage<TenantVO>> page(TenantQueryDTO query) {
        return R.ok(tenantService.page(query));
    }

    @SaCheckPermission("platform:tenant:list")
    @Operation(summary = "企业详情")
    @GetMapping("/{id}")
    public R<TenantVO> detail(@PathVariable Long id) {
        return R.ok(tenantService.detail(id));
    }

    @SaCheckPermission("platform:tenant:list")
    @Operation(summary = "平台首页统计")
    @GetMapping("/stats")
    public R<PlatformStatsVO> stats() {
        return R.ok(tenantService.stats());
    }

    @OperLog(module = "企业管理", action = "开通企业")
    @SaCheckPermission("platform:tenant:add")
    @Operation(summary = "开通企业")
    @PostMapping
    public R<Long> create(@Valid @RequestBody TenantCreateDTO dto) {
        return R.ok(tenantService.create(dto));
    }

    @OperLog(module = "企业管理", action = "修改企业")
    @SaCheckPermission("platform:tenant:edit")
    @Operation(summary = "修改企业")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TenantUpdateDTO dto) {
        tenantService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "企业管理", action = "启用/停用企业")
    @SaCheckPermission("platform:tenant:edit")
    @Operation(summary = "启用/停用企业")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        tenantService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    /** 返回被重置的管理员用户名 */
    @OperLog(module = "企业管理", action = "重置管理员密码", saveParams = false)
    @SaCheckPermission("platform:tenant:edit")
    @Operation(summary = "返回被重置的管理员用户名")
    @PutMapping("/{id}/admin-password")
    public R<String> resetAdminPassword(@PathVariable Long id, @Valid @RequestBody AdminPasswordDTO dto) {
        return R.ok(tenantService.resetAdminPassword(id, dto.getPassword()));
    }
}
