package com.safework.system.demo;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 按接口地址拦截演示企业的危险操作（规则见 DemoRules）。抛出的 BizException 由全局异常处理返回 403 */
@Component
@RequiredArgsConstructor
public class DemoInterceptor implements HandlerInterceptor {

    private final DemoGuard guard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC || !guard.props().isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        String what = DemoRules.blocked(request.getMethod(), uri);
        if (what != null) {
            guard.forbid(what);
        }
        return true;
    }
}
