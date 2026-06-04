package com.charging.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CaptchaController provides a simple text-based captcha generation endpoint.
 *
 * <p>MVP implementation: generates a 4-character alphanumeric code, stores it in Redis
 * with a 5-minute TTL, and returns the captchaId + captchaCode to the caller.
 * The code is returned in plain text for development/MVP convenience.
 *
 * <p>Production TODO: Replace with image-based captcha (e.g. PNG rendered code)
 * and return only the captchaId (not the code) to the client.
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_MINUTES = 5;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I,O,0,1 to avoid confusion

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * GET /api/v1/captcha
     * Generate a new captcha code and return its id and value.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateCode();

        String key = CAPTCHA_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, code, CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);

        return ResponseEntity.ok(Map.of(
                "captchaId", captchaId,
                "captchaCode", code
        ));
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}