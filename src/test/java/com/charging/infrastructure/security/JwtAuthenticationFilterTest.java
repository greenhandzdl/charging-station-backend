package com.charging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test JwtAuthenticationFilter using real JwtTokenProvider and a fake RedisTemplate.
 * Mockito cannot mock concrete/final classes on Java 25, so we use real instances
 * or manually-faked collaborators.
 */
class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;
    private FakeRedisTemplate redisTemplate;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();
        java.lang.reflect.Field secretField = JwtTokenProvider.class.getDeclaredField("jwtSecret");
        secretField.setAccessible(true);
        secretField.set(jwtTokenProvider, "test-secret-key-that-is-at-least-32-characters-long!!");

        java.lang.reflect.Field accessField = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        accessField.setAccessible(true);
        accessField.set(jwtTokenProvider, 900000L);

        java.lang.reflect.Field refreshField = JwtTokenProvider.class.getDeclaredField("refreshTokenExpiration");
        refreshField.setAccessible(true);
        refreshField.set(jwtTokenProvider, 604800000L);

        jwtTokenProvider.init();

        redisTemplate = new FakeRedisTemplate();
        filter = new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate);
        SecurityContextHolder.clearContext();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilterInternal_withNoToken_shouldContinueChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withNonBearerToken_shouldContinueChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic some-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withInvalidToken_shouldContinueChain() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer definitely-not-a-valid-jwt-token");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withBlacklistedToken_shouldReject() throws Exception {
        String token = jwtTokenProvider.generateAccessToken("user-1", "USER", "user");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Add token JTI to blacklist store
        String jti = jwtTokenProvider.getJtiFromToken(token);
        redisTemplate.addBlacklistKey(jti);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_withValidToken_shouldSetAuthentication() throws Exception {
        String token = jwtTokenProvider.generateAccessToken("user-123", "USER", "user");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_user")));
    }

    @Test
    void doFilterInternal_withAdminToken_shouldSetAdminRole() throws Exception {
        String token = jwtTokenProvider.generateAccessToken("admin-1", "ADMIN", "admin");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_admin")));
    }

    // A fake RedisTemplate that doesn't need a real connection.
    // The blacklist check finds no key by default, so non-blacklisted tokens pass through.
    private static class FakeRedisTemplate extends RedisTemplate<String, Object> {
        private final Map<String, Object> store = new HashMap<>();

        FakeRedisTemplate() {
        }

        @Override
        public Boolean hasKey(String key) {
            return store.containsKey(key);
        }

        void addBlacklistKey(String jti) {
            store.put("jwt:blacklist:" + jti, true);
        }
    }
}