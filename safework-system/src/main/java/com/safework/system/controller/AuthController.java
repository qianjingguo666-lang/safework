package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.ChangePasswordDTO;
import com.safework.system.dto.LoginDTO;
import com.safework.system.dto.LoginVO;
import com.safework.system.dto.UserInfoVO;
import com.safework.system.service.AuthService;
import com.safework.system.security.CaptchaService;
import com.safework.system.security.CaptchaVO;
import com.safework.system.service.PermissionService;
import com.safework.system.service.TenantService;
import com.safework.system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录认证", description = "验证码、登录、当前用户信息")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PermissionService permissionService;
    private final TenantService tenantService;
    private final CaptchaService captchaService;

    /** 获取图形验证码（不需要登录） */
    @Operation(summary = "获取图形验证码（不需要登录）")
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.create());
    }

    @Operation(summary = "登录（需要先获取验证码）")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return R.ok(authService.login(dto, request));
    }

    /** 当前用户信息 + 角色 + 权限码 + 菜单树 + 企业信息，前端登录后调用一次 */
    @Operation(summary = "当前用户信息 + 角色 + 权限码 + 菜单树 + 企业信息，前端登录后调用一次")
    @GetMapping("/me")
    public R<UserInfoVO> me() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(new UserInfoVO(
                userService.getById(userId),
                permissionService.getRoleCodes(userId),
                permissionService.getPermissions(userId),
                permissionService.getMenuTree(userId),
                tenantService.currentInfo()));
    }

    @Operation(summary = "退出当前会话")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    @OperLog(module = "个人中心", action = "修改密码", saveParams = false)
    @Operation(summary = "修改密码")
    @PostMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto.getOldPassword(), dto.getNewPassword());
        return R.ok();
    }
}
