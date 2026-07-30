package com.recloud.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Knife4j API 文档配置
 * <p>
 * 分组设计：
 * - 用户端 API：/api/** + /auth/**
 * - 管理端 API：/admin/**
 * <p>
 * 全局安全方案：Bearer Token（JWT）
 * <p>
 * 安全说明：仅在 dev/test 环境启用，生产环境禁用
 */
@Configuration
@Profile({"dev", "test"})
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReCloud 小说批注社区 API")
                        .version("2.0.0")
                        .description("基于 Spring Boot 3.2 + Spring Security + JWT 的小说批注社区系统")
                        .contact(new Contact().name("ReCloud Team"))
                )
                .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Auth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Access Token")
                        )
                );
    }

    /**
     * 用户端 API 分组
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户端 API")
                .pathsToMatch("/api/**", "/auth/**")
                .build();
    }

    /**
     * 管理端 API 分组
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端 API")
                .pathsToMatch("/admin/**")
                .build();
    }
}
