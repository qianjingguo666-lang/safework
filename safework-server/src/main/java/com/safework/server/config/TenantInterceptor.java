package com.safework.server.config;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysTenant;
import com.safework.system.service.AuthService;
import com.safework.system.service.TenantCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 租户拦截器（排在登录拦截器前面）：
 * 1. 已登录请求从会话中取出租户 ID 放入 TenantContext，请求结束后清理；
 * 2. 企业被停用 / 服务到期后，已经登录的用户下一次请求就会被拒绝（不用等 token 过期）；
 * 3. /platform/** 只有运营平台能访问 —— 和 @SaCheckPermission 一起构成两道防线。
 */
@Component
public class TenantInterceptor implements AsyncHandlerInterceptor {

    private final TenantCache tenantCache;

    public TenantInterceptor(TenantCache tenantCache) {
        this.tenantCache = tenantCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if (!StpUtil.isLogin()) {
            return true;
        }
        Object raw = StpUtil.getSession().get(AuthService.SESSION_TENANT_ID);
        if (raw == null) {
            return true;
        }
        Long tenantId = Long.valueOf(raw.toString());
        TenantContext.set(tenantId);

        if (!TenantContext.PLATFORM_ID.equals(tenantId)) {
            SysTenant tenant = tenantCache.getTenant(tenantId);
            String reason = tenant == null ? "企业不存在"
                    : tenant.getStatus() == null || tenant.getStatus() != 1 ? "企业已被停用，请联系平台管理员"
                    : tenant.expired() ? "企业服务已到期，请联系平台续费" : null;
            if (reason != null) {
                StpUtil.logout();
                reject(401, reason);
            }
        }

        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/platform/") && !TenantContext.isPlatform()) {
            reject(403, "只有平台管理员可以访问");
        }
        return true;
    }

    /**
     * preHandle 抛异常时，Spring 不会调用这个拦截器自己的 afterCompletion，
     * 所以抛之前必须先清理 TenantContext，否则会残留在线程上
     */
    private static void reject(int code, String msg) {
        TenantContext.clear();
        throw new BizException(code, msg);
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        // 线程池会复用线程，不清理会把上一个人的租户带到下一个请求
        TenantContext.clear();
    }

    /**
     * 异步请求（AI 流式回答 SSE）：控制器返回后请求线程就被释放了，不会调用 afterCompletion，
     * 必须在这里清理，否则这个 Tomcat 线程会带着上一个企业的租户 ID 去处理别人的请求
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        TenantContext.clear();
    }
}
