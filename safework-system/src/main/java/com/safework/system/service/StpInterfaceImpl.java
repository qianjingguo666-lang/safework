package com.safework.system.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 在校验 @SaCheckPermission / @SaCheckRole 时调用这里获取权限。
 * 调用发生在请求线程中，此时 TenantContext 已经设置好，查询会自动带上租户条件。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionService permissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionService.getPermissions(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return permissionService.getRoleCodes(Long.valueOf(loginId.toString()));
    }
}
