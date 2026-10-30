package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.core.R;
import com.safework.system.dto.MenuVO;
import com.safework.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "菜单")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final PermissionService permissionService;

    /** 全部菜单树（含按钮），用于给角色勾选权限 */
    @SaCheckPermission("system:menu:list")
    @Operation(summary = "全部菜单树（含按钮），用于给角色勾选权限")
    @GetMapping("/tree")
    public R<List<MenuVO>> tree() {
        return R.ok(permissionService.getAllMenuTree());
    }
}
