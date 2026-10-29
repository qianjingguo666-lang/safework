package com.safework.system.controller.platform;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.MenuVO;
import com.safework.system.dto.StatusDTO;
import com.safework.system.dto.platform.PackageSaveDTO;
import com.safework.system.dto.platform.PackageVO;
import com.safework.system.entity.SysTenantPackage;
import com.safework.system.service.TenantPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/package")
@RequiredArgsConstructor
public class PlatformPackageController {

    private final TenantPackageService packageService;

    @SaCheckPermission("platform:package:list")
    @GetMapping("/list")
    public R<List<PackageVO>> list() {
        return R.ok(packageService.list());
    }

    /** 开通企业时选择套餐 */
    @SaCheckPermission("platform:tenant:list")
    @GetMapping("/options")
    public R<List<SysTenantPackage>> options() {
        return R.ok(packageService.options());
    }

    /** 可以放进套餐的全部企业端菜单 */
    @SaCheckPermission("platform:package:list")
    @GetMapping("/menu-tree")
    public R<List<MenuVO>> menuTree() {
        return R.ok(packageService.menuTree());
    }

    @OperLog(module = "套餐管理", action = "新增套餐")
    @SaCheckPermission("platform:package:edit")
    @PostMapping
    public R<Long> create(@Valid @RequestBody PackageSaveDTO dto) {
        return R.ok(packageService.create(dto));
    }

    @OperLog(module = "套餐管理", action = "修改套餐")
    @SaCheckPermission("platform:package:edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PackageSaveDTO dto) {
        packageService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "套餐管理", action = "启用/停用套餐")
    @SaCheckPermission("platform:package:edit")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        packageService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    @OperLog(module = "套餐管理", action = "删除套餐")
    @SaCheckPermission("platform:package:edit")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        packageService.remove(id);
        return R.ok();
    }
}
