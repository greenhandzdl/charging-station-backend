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
                .pathsToMatch("/v1/auth/register",
                        "/v1/auth/login",
                        "/v1/auth/password-reset",
                        "/v1/auth/password-reset/confirm",
                        "/v1/captcha/**",
                        "/v1/payments/callback",
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
                .pathsToMatch("/v1/charges/**",
                        "/v1/stations/**",
                        "/v1/chargers/**",
                        "/v1/payments/**",
                        "/v1/repairs/**",
                        "/v1/auth/refresh",
                        "/v1/auth/password",
                        "/v1/users/balance")
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
                .pathsToMatch("/v1/users/**",
                        "/v1/analytics/**")
                .pathsToExclude("/v1/users/balance")
                .build();
    }
}