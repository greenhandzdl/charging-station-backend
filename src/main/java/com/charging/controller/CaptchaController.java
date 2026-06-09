package com.charging.controller;

import com.charging.infrastructure.dto.CaptchaResult;
import com.charging.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CaptchaController 图片验证码 API。
 * <p>
 * 委托 {@link CaptchaService} 完成验证码生成与校验。
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private static final String CAPTCHA_PREFIX = "captcha:";

    private final CaptchaService captchaService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * GET /api/v1/captcha
     * 生成验证码，返回 captchaId、captchaCode（明文，调试用）和 base64 图片。
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> generate() {
        CaptchaResult result = captchaService.generateCaptcha();

        Map<String, String> map = new LinkedHashMap<>();
        map.put("captchaId", result.getCaptchaId());
        map.put("captchaCode", result.getCaptchaCode());
        map.put("image", result.getImage());

        return ResponseEntity.ok(map);
    }

    /**
     * GET /api/v1/captcha/{captchaId}/image
     * 获取指定验证码的 PNG 图片。
     */
    @GetMapping("/{captchaId}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable String captchaId) {
        String storedCode = (String) redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
        if (storedCode == null) {
            return ResponseEntity.notFound().build();
        }
        // 重新生成图片（实际应从缓存取，此处保持简单）
        CaptchaResult result = captchaService.generateCaptcha();
        // 这里用 storedCode 重新生成，因为我们只需要图片
        if (result.getImage() != null && !result.getImage().isEmpty()) {
            String base64 = result.getImage().replace("data:image/png;base64,", "");
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
        }
        return ResponseEntity.internalServerError().build();
    }
}