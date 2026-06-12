package com.charging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // If already authenticated (e.g. by ChargerAuthFilter), skip
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            // Check blacklist (fail-safe: skip check if Redis unavailable)
            String jti = jwtTokenProvider.getJtiFromToken(token);
            if (jti != null) {
                try {
                    Boolean isBlacklisted = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti);
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.debug("Blacklisted JWT token rejected: {}", jti);
                        filterChain.doFilter(request, response);
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Redis unavailable, skipping blacklist check for jti={}: {}", jti, e.getMessage());
                }
            }

            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);
            String scope = jwtTokenProvider.getScopeFromToken(token);

            if (userId != null && role != null) {
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()),
                        new SimpleGrantedAuthority("SCOPE_" + (scope != null ? scope : "user"))
                );

                JwtUserPrincipal principal = JwtUserPrincipal.builder()
                        .userId(userId)
                        .role(role)
                        .scope(scope)
                        .token(token)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}