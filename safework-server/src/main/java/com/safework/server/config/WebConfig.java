package com.safework.server.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.safework.common.tenant.TenantContext;
import com.safework.system.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 不需要登录的接口 */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/actuator/health",
            "/error"
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先设置租户：已登录请求从会话中取出租户 ID 放入 TenantContext，请求结束后清理。
        //    必须排在登录拦截器前面，因为 @SaCheckPermission 校验时会查数据库，需要租户信息
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
                if (StpUtil.isLogin()) {
                    Object tenantId = StpUtil.getSession().get(AuthService.SESSION_TENANT_ID);
                    if (tenantId != null) {
                        TenantContext.set(Long.valueOf(tenantId.toString()));
                    }
                }
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
                // 线程池会复用线程，不清理会把上一个人的租户带到下一个请求
                TenantContext.clear();
            }
        }).addPathPatterns("/**");

        // 2. 登录校验 + 权限注解校验（@SaCheckPermission）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(WHITE_LIST);
    }

    /** Long 转成字符串返回，避免前端 JS 雪花 ID 精度丢失 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
