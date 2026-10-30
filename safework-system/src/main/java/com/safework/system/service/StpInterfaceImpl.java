package com.safework.system.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sa-Token 在校验 @SaCheckPermission / @SaCheckRole / StpUtil.hasPermission 时调用这里获取权限。
 * 调用发生在请求线程中，此时 TenantContext 已经设置好，查询会自动带上租户条件。
 * <p>
 * 一个请求里可能校验好几次权限（注解一次，业务代码里 hasPermission 判断数据范围又几次），
 * 每次都要查角色、菜单、套餐。这里按请求缓存：同一个请求只查一次，请求结束自动丢弃，
 * 不会出现“改了权限要等缓存过期”的问题。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionService permissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        return cached("perms:" + userId, () -> permissionService.getPermissions(userId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        return cached("roles:" + userId, () -> permissionService.getRoleCodes(userId));
    }

    @SuppressWarnings("unchecked")
    private static List<String> cached(String key, Supplier<List<String>> loader) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return loader.get();
        }
        String attrKey = "safework.stp." + key;
        Object value = attrs.getAttribute(attrKey, RequestAttributes.SCOPE_REQUEST);
        if (value == null) {
            value = loader.get();
            attrs.setAttribute(attrKey, value, RequestAttributes.SCOPE_REQUEST);
        }
        return (List<String>) value;
    }
}
