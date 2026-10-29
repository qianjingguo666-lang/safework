package com.safework.common.tenant;

import java.util.function.Supplier;

/**
 * 当前请求所属租户。每个请求进来时由拦截器设置，请求结束时清除。
 */
public final class TenantContext {

    /** 运营平台自己的租户 ID（企业编码 platform），平台管理员登录后就是这个租户 */
    public static final Long PLATFORM_ID = 0L;

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        HOLDER.set(tenantId);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static boolean isPlatform() {
        return PLATFORM_ID.equals(HOLDER.get());
    }

    /**
     * 临时切换到另一个租户执行一段代码，结束后恢复原来的租户。
     * 用于平台开通企业（在新企业下创建管理员、角色）、登录（还没登录时按企业查用户）等场景。
     */
    public static <T> T callAs(Long tenantId, Supplier<T> action) {
        Long previous = HOLDER.get();
        HOLDER.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        }
    }

    public static void runAs(Long tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
