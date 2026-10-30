package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.IdsDTO;
import com.safework.system.dto.RoleCreateDTO;
import com.safework.system.entity.SysRole;
import com.safework.system.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @SaCheckPermission("system:role:list")
    @Operation(summary = "角色列表")
    @GetMapping("/list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    @OperLog(module = "角色管理", action = "新增角色")
    @SaCheckPermission("system:role:add")
    @Operation(summary = "新增角色")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RoleCreateDTO dto) {
        return R.ok(roleService.create(dto));
    }

    @SaCheckPermission("system:role:menu")
    @Operation(summary = "角色已分配的菜单")
    @GetMapping("/{id}/menus")
    public R<List<Long>> menuIds(@PathVariable Long id) {
        return R.ok(roleService.getMenuIds(id));
    }

    @OperLog(module = "角色管理", action = "分配权限")
    @SaCheckPermission("system:role:menu")
    @Operation(summary = "分配权限")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody IdsDTO dto) {
        roleService.assignMenus(id, dto.getIds());
        return R.ok();
    }
}
