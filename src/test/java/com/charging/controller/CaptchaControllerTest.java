package com.charging.controller;

import com.charging.infrastructure.dto.CaptchaResult;
import com.charging.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptchaControllerTest {

    @Mock
    private CaptchaService captchaService;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CaptchaController captchaController;
    private FakeRedisTemplate redisTemplate;

    /**
     * RedisTemplate subclass that delegates opsForValue() to the mock,
     * avoiding Mockito's JDK-25 final-class compatibility issue with RedisTemplate.
     */
    static class FakeRedisTemplate extends RedisTemplate<String, Object> {
        private final ValueOperations<String, Object> valueOps;

        FakeRedisTemplate(ValueOperations<String, Object> valueOps) {
            this.valueOps = valueOps;
        }

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOps;
        }
    }

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeRedisTemplate(valueOperations);
        captchaController = new CaptchaController(captchaService, redisTemplate);
    }

    @Test
    void generate_shouldReturnCaptchaWithAllFields() {
        CaptchaResult result = CaptchaResult.builder()
                .captchaId("test-captcha-id")
                .captchaCode("ABCD")
                .image("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg")
                .build();
        when(captchaService.generateCaptcha()).thenReturn(result);

        ResponseEntity<Map<String, String>> response = captchaController.generate();

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("test-captcha-id", response.getBody().get("captchaId"));
        assertEquals("ABCD", response.getBody().get("captchaCode"));
        assertTrue(response.getBody().get("image").startsWith("data:image/png;base64,"));
        verify(captchaService).generateCaptcha();
    }

    @Test
    void generate_shouldReturnDifferentCaptchaOnMultipleCalls() {
        when(captchaService.generateCaptcha())
                .thenReturn(CaptchaResult.builder().captchaId("id-1").captchaCode("ABCD").image("img1").build())
                .thenReturn(CaptchaResult.builder().captchaId("id-2").captchaCode("EFGH").image("img2").build());

        ResponseEntity<Map<String, String>> first = captchaController.generate();
        ResponseEntity<Map<String, String>> second = captchaController.generate();

        assertNotNull(first.getBody());
        assertNotNull(second.getBody());
        assertNotEquals(first.getBody().get("captchaId"), second.getBody().get("captchaId"));
        verify(captchaService, times(2)).generateCaptcha();
    }

    @Test
    void getImage_withValidCaptchaId_shouldReturnPngBytes() {
        String captchaId = "test-captcha-id";
        String key = "captcha:" + captchaId;
        byte[] rawImageBytes = "fake-png-bytes".getBytes();
        String base64Image = Base64.getEncoder().encodeToString(rawImageBytes);

        when(valueOperations.get(key)).thenReturn("ABCD");
        when(captchaService.generateCaptcha()).thenReturn(
                CaptchaResult.builder()
                        .captchaId(captchaId)
                        .captchaCode("ABCD")
                        .image("data:image/png;base64," + base64Image)
                        .build()
        );

        ResponseEntity<byte[]> response = captchaController.getImage(captchaId);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertArrayEquals(rawImageBytes, response.getBody());
        verify(valueOperations).get(key);
    }

    @Test
    void getImage_withExpiredCaptchaId_shouldReturn404() {
        String captchaId = "expired-captcha-id";
        String key = "captcha:" + captchaId;

        when(valueOperations.get(key)).thenReturn(null);

        ResponseEntity<byte[]> response = captchaController.getImage(captchaId);

        assertEquals(404, response.getStatusCodeValue());
        verify(valueOperations).get(key);
        verify(captchaService, never()).generateCaptcha();
    }

    @Test
    void getImage_whenImageGenerationFails_shouldReturn500() {
        String captchaId = "test-captcha-id";
        String key = "captcha:" + captchaId;

        when(valueOperations.get(key)).thenReturn("ABCD");
        when(captchaService.generateCaptcha()).thenReturn(
                CaptchaResult.builder()
                        .captchaId(captchaId)
                        .captchaCode("ABCD")
                        .image("")
                        .build()
        );

        ResponseEntity<byte[]> response = captchaController.getImage(captchaId);

        assertEquals(500, response.getStatusCodeValue());
    }
}
