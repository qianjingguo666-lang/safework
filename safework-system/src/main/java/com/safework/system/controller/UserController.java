package com.safework.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.core.R;
import com.safework.system.dto.IdsDTO;
import com.safework.system.dto.UserCreateDTO;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @SaCheckPermission("system:user:list")
    @GetMapping("/page")
    public R<Page<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "10") long size,
                                 @RequestParam(required = false) String keyword) {
        return R.ok(userService.page(current, size, keyword));
    }

    @SaCheckPermission("system:user:add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return R.ok(userService.create(dto));
    }

    @SaCheckPermission("system:user:role")
    @GetMapping("/{id}/roles")
    public R<List<Long>> roleIds(@PathVariable Long id) {
        return R.ok(userService.getRoleIds(id));
    }

    @SaCheckPermission("system:user:role")
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody IdsDTO dto) {
        userService.assignRoles(id, dto.getIds());
        return R.ok();
    }
}
