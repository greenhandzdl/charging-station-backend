package com.charging.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // Use reflection to set the secret and expiration fields since @Value won't work without Spring
        setField(jwtTokenProvider, "jwtSecret", "test-secret-key-that-is-at-least-32-characters-long!!");
        setField(jwtTokenProvider, "accessTokenExpiration", 900000L);
        setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);
        jwtTokenProvider.init();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateAccessToken_shouldReturnValidToken() {
        String token = jwtTokenProvider.generateAccessToken("user-123", "USER", "user");

        assertNotNull(token);
        assertFalse(token.isBlank());

        // Verify we can read it back
        assertEquals("user-123", jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("USER", jwtTokenProvider.getRoleFromToken(token));
        assertEquals("user", jwtTokenProvider.getScopeFromToken(token));
    }

    @Test
    void generateAccessToken_withNullScope_shouldDefaultToUser() {
        String token = jwtTokenProvider.generateAccessToken("user-456", "ADMIN", null);

        assertNotNull(token);
        assertEquals("user-456", jwtTokenProvider.getUserIdFromToken(token));
        assertEquals("ADMIN", jwtTokenProvider.getRoleFromToken(token));
        assertEquals("user", jwtTokenProvider.getScopeFromToken(token));
    }

    @Test
    void generateRefreshToken_shouldReturnValidToken() {
        String token = jwtTokenProvider.generateRefreshToken("user-123");

        assertNotNull(token);
        assertEquals("user-123", jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    void getJtiFromToken_shouldReturnUniqueIds() {
        String token1 = jwtTokenProvider.generateAccessToken("u1", "USER", "user");
        String token2 = jwtTokenProvider.generateAccessToken("u1", "USER", "user");

        String jti1 = jwtTokenProvider.getJtiFromToken(token1);
        String jti2 = jwtTokenProvider.getJtiFromToken(token2);

        assertNotNull(jti1);
        assertNotNull(jti2);
        assertNotEquals(jti1, jti2);
    }

    @Test
    void validateToken_withInvalidToken_shouldReturnNull() {
        assertNull(jwtTokenProvider.validateToken("invalid-token"));
        assertNull(jwtTokenProvider.validateToken(""));
        assertNull(jwtTokenProvider.validateToken(null));
    }

    @Test
    void isTokenExpired_withValidToken_shouldReturnFalse() {
        String token = jwtTokenProvider.generateAccessToken("user-123", "USER", "user");

        assertFalse(jwtTokenProvider.isTokenExpired(token));
    }

    @Test
    void isTokenExpired_withInvalidToken_shouldReturnTrue() {
        assertTrue(jwtTokenProvider.isTokenExpired("invalid-token"));
    }

    @Test
    void getRoleFromToken_withInvalidToken_shouldReturnNull() {
        assertNull(jwtTokenProvider.getRoleFromToken("invalid-token"));
    }

    @Test
    void getExpirationValues_shouldReturnConfiguredValues() {
        assertEquals(900000L, jwtTokenProvider.getAccessTokenExpiration());
        assertEquals(604800000L, jwtTokenProvider.getRefreshTokenExpiration());
    }
}