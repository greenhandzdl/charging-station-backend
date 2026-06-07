package com.charging.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * SwaggerConfig configures springdoc-openapi for API documentation.
 *
 * <p>Three API groups with progressively restrictive access:
 * <ul>
 *   <li><b>Public</b> — auth endpoints, captcha, health check (no auth required)</li>
 *   <li><b>Authenticated</b> — charging, station/charger reads, payments, repairs (JWT required)</li>
 *   <li><b>Admin</b> — user management, station/charger writes, analytics (ADMIN/SUPER_ADMIN required)</li>
 * </ul>
 *
 * <p>Swagger UI: <a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a>
 *
 * <p>This configuration is only active when the {@code dev} profile is enabled.
 */
@Configuration
@Profile("dev")
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("充电站管理系统 API").version("1.0.0").description("开发环境 API 文档"))
                .servers(List.of(new Server().url("http://localhost:8080").description("开发服务器")));
    }

    /**
     * Public API group — endpoints accessible without authentication.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("公开接口（无需认证）")
                .pathsToMatch("/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/password-reset",
                        "/api/v1/auth/password-reset/confirm",
                        "/api/v1/captcha/**",
                        "/api/v1/payments/callback",
                        "/actuator/health")
                .build();
    }

    /**
     * Authenticated API group — endpoints requiring a valid JWT token.
     */
    @Bean
    public GroupedOpenApi authenticatedApi() {
        return GroupedOpenApi.builder()
                .group("authenticated")
                .displayName("认证接口（需JWT）")
                .pathsToMatch("/api/v1/charges/**",
                        "/api/v1/stations/**",
                        "/api/v1/chargers/**",
                        "/api/v1/payments/**",
                        "/api/v1/repairs/**",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password",
                        "/api/v1/users/balance")
                .build();
    }

    /**
     * Admin API group — endpoints restricted to ADMIN / SUPER_ADMIN roles.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("管理接口（需ADMIN/SUPER_ADMIN）")
                .pathsToMatch("/api/v1/users/**",
                        "/api/v1/analytics/**")
                .pathsToExclude("/api/v1/users/balance")
                .build();
    }
}