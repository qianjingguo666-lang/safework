package com.safework.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在线接口文档：http://localhost:8080/swagger-ui.html（只在开发环境开放）
 * 先调用 /auth/captcha、/auth/login 拿到 token，点右上角 Authorize 填进去，就能直接在页面上调试接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-token";

    @Bean
    public OpenAPI safeworkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SafeWork 智能安全作业管理平台 API")
                        .version("1.0.0")
                        .description("统一返回 {code, msg, data}；时间格式 yyyy-MM-dd HH:mm:ss；Long 类型的 ID 以字符串返回。"
                                + "除登录、验证码、扫码页外都需要在请求头带 Authorization: Bearer <token>"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("登录接口返回的 token（不用加 Bearer 前缀）")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** 按业务分组，页面右上角可以切换 */
    @Bean
    public GroupedOpenApi systemApi() {
        return group("1-系统管理", "/auth/**", "/system/**", "/message/**", "/file/**");
    }

    @Bean
    public GroupedOpenApi ticketApi() {
        return group("2-作业票与看板", "/ticket/**", "/dashboard/**");
    }

    @Bean
    public GroupedOpenApi safetyApi() {
        return group("3-安全管理", "/safety/**", "/public/**");
    }

    @Bean
    public GroupedOpenApi aiApi() {
        return group("4-AI 助手", "/ai/**");
    }

    @Bean
    public GroupedOpenApi platformApi() {
        return group("5-平台端", "/platform/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }
}
