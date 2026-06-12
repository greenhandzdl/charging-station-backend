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
import java.util.ArrayList;
import java.util.List;

/**
 * 充电桩/站 JWT 认证过滤器。
 * 解析 X-Charger-Token 或 Authorization: Bearer <charger-jwt> 头中的充电桩 JWT，
 * 验证 permissionLevel + tokenVersion，设置安全上下文（scope=charger）。
 *
 * <p>权限体系：CHARGER（单桩）< STATION（站级）< STATION_GLOBAL（全局）
 * <ul>
 *   <li>CHARGER — 只能操作绑定的单个充电桩（ROLE_CHARGER）</li>
 *   <li>STATION — 可操作站下所有充电桩（ROLE_STATION）</li>
 *   <li>STATION_GLOBAL — 可操作任意充电桩（ROLE_STATION_GLOBAL）</li>
 * </ul>
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

        // 如果已经有认证（例如 JwtAuthenticationFilter 已处理），则跳过
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            var claims = chargerTokenProvider.validateToken(token);
            if (claims != null) {
                String scope = claims.get("scope", String.class);
                if ("charger".equals(scope)) {
                    String chargerUserId = claims.getSubject();
                    String permissionLevel = claims.get("permissionLevel", String.class);
                    Integer tokenVersion = claims.get("tokenVersion", Integer.class);

                    // Build authorities based on permission level
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("SCOPE_charger"));
                    if (permissionLevel != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + permissionLevel));
                    }

                    JwtUserPrincipal principal = JwtUserPrincipal.builder()
                            .userId(chargerUserId)
                            .role(permissionLevel != null ? permissionLevel : "CHARGER")
                            .scope("charger")
                            .token(token)
                            .build();

                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Charger JWT auth success: chargerUserId={}, permissionLevel={}",
                            chargerUserId, permissionLevel);
                }
            } else {
                log.debug("Invalid charger JWT token");
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String chargerToken = request.getHeader(CHARGER_TOKEN_HEADER);
        if (StringUtils.hasText(chargerToken)) {
            return chargerToken.trim();
        }
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }
}