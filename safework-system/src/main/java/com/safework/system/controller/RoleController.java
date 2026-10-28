package com.safework.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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

@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    @SaCheckPermission("system:role:add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RoleCreateDTO dto) {
        return R.ok(roleService.create(dto));
    }

    @SaCheckPermission("system:role:menu")
    @GetMapping("/{id}/menus")
    public R<List<Long>> menuIds(@PathVariable Long id) {
        return R.ok(roleService.getMenuIds(id));
    }

    @SaCheckPermission("system:role:menu")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody IdsDTO dto) {
        roleService.assignMenus(id, dto.getIds());
        return R.ok();
    }
}
