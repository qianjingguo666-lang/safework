package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.IdsDTO;
import com.safework.system.dto.ResetPasswordDTO;
import com.safework.system.dto.StatusDTO;
import com.safework.system.dto.UserCreateDTO;
import com.safework.system.dto.UserOptionVO;
import com.safework.system.dto.UserUpdateDTO;
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

@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @SaCheckPermission("system:user:list")
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "10") long size,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Long deptId,
                                 @RequestParam(required = false) Integer status) {
        return R.ok(userService.page(current, size, keyword, deptId, status));
    }

    /** 下拉选择：登录即可调用（如选择隐患整改人） */
    @Operation(summary = "下拉选择")
    @GetMapping("/options")
    public R<List<UserOptionVO>> options() {
        return R.ok(userService.options());
    }

    @OperLog(module = "用户管理", action = "新增用户")
    @SaCheckPermission("system:user:add")
    @Operation(summary = "新增用户")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateDTO dto) {
        return R.ok(userService.create(dto));
    }

    @OperLog(module = "用户管理", action = "修改用户")
    @SaCheckPermission("system:user:edit")
    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "用户管理", action = "启用/停用")
    @SaCheckPermission("system:user:edit")
    @Operation(summary = "启用/停用")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        userService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    @OperLog(module = "用户管理", action = "重置密码")
    @SaCheckPermission("system:user:reset")
    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(id, dto.getPassword());
        return R.ok();
    }

    @OperLog(module = "用户管理", action = "解锁账号")
    @SaCheckPermission("system:user:unlock")
    @Operation(summary = "解锁账号")
    @PutMapping("/{id}/unlock")
    public R<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return R.ok();
    }

    @SaCheckPermission("system:user:role")
    @Operation(summary = "用户已分配的角色")
    @GetMapping("/{id}/roles")
    public R<List<Long>> roleIds(@PathVariable Long id) {
        return R.ok(userService.getRoleIds(id));
    }

    @OperLog(module = "用户管理", action = "分配角色")
    @SaCheckPermission("system:user:role")
    @Operation(summary = "分配角色")
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody IdsDTO dto) {
        userService.assignRoles(id, dto.getIds());
        return R.ok();
    }
}
