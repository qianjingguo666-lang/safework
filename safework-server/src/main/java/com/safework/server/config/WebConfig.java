package com.safework.server.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 不需要登录的接口 */
    private static final List<String> WHITE_LIST = List.of(
            "/auth/login",
            "/auth/captcha",
            // 扫码查看受限空间：现场人员不需要登录
            "/public/**",
            "/actuator/health",
            "/error",
            // 接口文档页面本身不需要登录（生产环境已关闭文档，这几个地址直接 404）
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private final TenantInterceptor tenantInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 先设置租户（并检查企业状态）。必须排在登录拦截器前面，因为 @SaCheckPermission 校验时会查数据库，需要租户信息
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/**");

        // 2. 登录校验 + 权限注解校验（@SaCheckPermission）
        // 长连接（SSE）结束时 Spring 会把请求再分发一次（ASYNC），第一次已经校验过登录和权限，这里跳过；
        // 否则被踢下线的用户会在一个已经输出过事件流的响应里再报一次 401
        HandlerInterceptor sa = new SaInterceptor(handle -> StpUtil.checkLogin());
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                        return request.getDispatcherType() == DispatcherType.ASYNC || sa.preHandle(request, response, handler);
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns(WHITE_LIST);
    }

    /**
     * JSON 全局配置：
     * 1. Long 转成字符串返回，避免前端 JS 雪花 ID 精度丢失
     * 2. LocalDateTime 统一使用 yyyy-MM-dd HH:mm:ss 格式（入参和出参）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializers(new LocalDateTimeSerializer(fmt))
                .deserializers(new LocalDateTimeDeserializer(fmt));
    }
}
