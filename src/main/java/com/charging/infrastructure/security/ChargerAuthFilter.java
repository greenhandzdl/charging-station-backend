package com.charging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 充电桩 JWT 认证过滤器。
 * 解析 X-Charger-Token 或 Authorization: Bearer <charger-jwt> 头中的充电桩 JWT，
 * 验证后设置安全上下文（scope=charger, role=CHARGER）。
 * 仅对 scope=charger 的令牌进行处理，普通用户 JWT 由 JwtAuthenticationFilter 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class ChargerAuthFilter extends OncePerRequestFilter {

    private static final String CHARGER_TOKEN_HEADER = "X-Charger-Token";

    private final ChargerTokenProvider chargerTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            var claims = chargerTokenProvider.validateToken(token);
            if (claims != null) {
                String scope = claims.get("scope", String.class);
                if ("charger".equals(scope)) {
                    String chargerUserId = claims.getSubject();
                    String chargerId = claims.get("chargerId", String.class);
                    String identityType = claims.get("identityType", String.class);

                    var authorities = List.of(
                            new SimpleGrantedAuthority("SCOPE_charger"),
                            new SimpleGrantedAuthority("ROLE_CHARGER")
                    );

                    JwtUserPrincipal principal = JwtUserPrincipal.builder()
                            .userId(chargerUserId)
                            .role("CHARGER")
                            .scope("charger")
                            .token(token)
                            .build();

                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Charger JWT auth success: chargerUserId={}, chargerId={}, identityType={}",
                            chargerUserId, chargerId, identityType);
                }
            } else {
                log.debug("Invalid charger JWT token");
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // First try custom header
        String chargerToken = request.getHeader(CHARGER_TOKEN_HEADER);
        if (StringUtils.hasText(chargerToken)) {
            return chargerToken.trim();
        }
        // Then try standard Authorization header
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }
}