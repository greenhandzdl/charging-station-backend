package com.charging.service;

import com.charging.infrastructure.dto.CaptchaResult;
import com.charging.service.impl.RedisCaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CaptchaService captchaService;
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * A minimal RedisTemplate subclass that delegates opsForValue() to the mock
     * and tracks delete() calls for verification.
     */
    static class MockRedisTemplate extends RedisTemplate<String, Object> {
        private final ValueOperations<String, Object> valueOps;
        private String deletedKey;

        MockRedisTemplate(ValueOperations<String, Object> valueOps) {
            this.valueOps = valueOps;
        }

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOps;
        }

        @Override
        public Boolean delete(String key) {
            this.deletedKey = key;
            return true;
        }

        String getDeletedKey() {
            return deletedKey;
        }

        void resetDelete() {
            deletedKey = null;
        }
    }

    @BeforeEach
    void setUp() {
        MockRedisTemplate template = new MockRedisTemplate(valueOperations);
        this.redisTemplate = template;
        captchaService = new RedisCaptchaService(template);
    }

    @Test
    void generateCaptcha_shouldReturnValidCaptchaId() {
        CaptchaResult result = captchaService.generateCaptcha();

        assertNotNull(result);
        assertNotNull(result.getCaptchaId());
        assertDoesNotThrow(() -> UUID.fromString(result.getCaptchaId()));
    }

    @Test
    void generateCaptcha_shouldReturn4CharCodeFromValidCharset() {
        CaptchaResult result = captchaService.generateCaptcha();

        assertNotNull(result.getCaptchaCode());
        assertEquals(4, result.getCaptchaCode().length());
        assertTrue(result.getCaptchaCode().matches("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}$"));
    }

    @Test
    void generateCaptcha_shouldReturnValidImageOrEmptyInHeadless() {
        CaptchaResult result = captchaService.generateCaptcha();

        assertNotNull(result.getImage());
        assertTrue(result.getImage().isEmpty()
                || result.getImage().startsWith("data:image/png;base64,"));
    }

    @Test
    void generateCaptcha_shouldStoreInRedisWithTtl() {
        captchaService.generateCaptcha();

        verify(valueOperations).set(startsWith("captcha:"), anyString(),
                eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void validateCaptcha_shouldReturnTrueForCorrectCode() {
        String captchaId = UUID.randomUUID().toString();
        String code = "AbCd";
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn(code);

        assertTrue(captchaService.validateCaptcha(captchaId, code));
    }

    @Test
    void validateCaptcha_shouldReturnFalseForWrongCode() {
        String captchaId = UUID.randomUUID().toString();
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn("AbCd");

        assertFalse(captchaService.validateCaptcha(captchaId, "wrong"));
    }

    @Test
    void validateCaptcha_shouldBeCaseInsensitive() {
        String captchaId = UUID.randomUUID().toString();
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn("XDKq");

        assertTrue(captchaService.validateCaptcha(captchaId, "xdkQ"));
    }

    @Test
    void validateCaptcha_shouldReturnFalseForNullCaptchaId() {
        assertFalse(captchaService.validateCaptcha(null, "ABCD"));
    }

    @Test
    void validateCaptcha_shouldReturnFalseForNullCode() {
        assertFalse(captchaService.validateCaptcha("some-id", null));
    }

    @Test
    void validateCaptcha_shouldReturnFalseForBothNull() {
        assertFalse(captchaService.validateCaptcha(null, null));
    }

    @Test
    void validateCaptcha_shouldReturnFalseForExpiredCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn(null);

        assertFalse(captchaService.validateCaptcha(captchaId, "ABCD"));
    }

    @Test
    void validateCaptcha_shouldDeleteKeyAfterSuccessfulValidation() {
        String captchaId = UUID.randomUUID().toString();
        String code = "XYZ2";
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn(code);

        // Reset delete tracker
        MockRedisTemplate mockTemplate = (MockRedisTemplate) redisTemplate;
        mockTemplate.resetDelete();

        boolean result = captchaService.validateCaptcha(captchaId, code);

        assertTrue(result);
        assertEquals(key, mockTemplate.getDeletedKey());
    }

    @Test
    void validateCaptcha_shouldNotDeleteKeyForWrongCode() {
        String captchaId = UUID.randomUUID().toString();
        String key = "captcha:" + captchaId;
        when(valueOperations.get(key)).thenReturn("ABCD");

        MockRedisTemplate mockTemplate = (MockRedisTemplate) redisTemplate;
        mockTemplate.resetDelete();

        captchaService.validateCaptcha(captchaId, "wrong");

        assertNull(mockTemplate.getDeletedKey());
    }

    @Test
    void generateCaptcha_shouldReturnDifferentIdsOnMultipleCalls() {
        CaptchaResult first = captchaService.generateCaptcha();
        CaptchaResult second = captchaService.generateCaptcha();

        assertNotNull(first.getCaptchaId());
        assertNotNull(second.getCaptchaId());
        assertNotEquals(first.getCaptchaId(), second.getCaptchaId());
    }
}