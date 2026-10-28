package com.safework.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.core.R;
import com.safework.system.dto.LoginDTO;
import com.safework.system.dto.LoginVO;
import com.safework.system.dto.UserInfoVO;
import com.safework.system.service.AuthService;
import com.safework.system.service.PermissionService;
import com.safework.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PermissionService permissionService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 当前用户信息 + 角色 + 权限码 + 菜单树，前端登录后调用一次 */
    @GetMapping("/me")
    public R<UserInfoVO> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(new UserInfoVO(
                userService.getById(userId),
                permissionService.getRoleCodes(userId),
                permissionService.getPermissions(userId),
                permissionService.getMenuTree(userId)));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        StpUtil.logout();
        return R.ok();
    }
}
