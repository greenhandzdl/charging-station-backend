package com.charging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 高级权限密钥过滤器。
 * 当请求头 X-Advanced-Api-Key 匹配配置的密钥时，授予高级权限。
 * 仅在测试环境开放（由 advanced.api-key.enabled 控制）。
 */
@Slf4j
@Component
@Order(0)
public class AdvancedApiKeyFilter extends OncePerRequestFilter {

    @Value("${advanced.api-key.key:}")
    private String apiKey;

    @Value("${advanced.api-key.enabled:false}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (enabled && !apiKey.isEmpty()) {
            String providedKey = request.getHeader("X-Advanced-Api-Key");
            if (providedKey != null && providedKey.equals(apiKey)) {
                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                        new SimpleGrantedAuthority("SCOPE_advanced")
                );
                var principal = new JwtUserPrincipal("advanced-user", "SUPER_ADMIN", "advanced", null);
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Advanced API Key authentication successful");
            }
        }
        filterChain.doFilter(request, response);
    }
}
