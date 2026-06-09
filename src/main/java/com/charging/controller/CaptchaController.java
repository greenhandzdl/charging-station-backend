package com.charging.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * CaptchaController 生成图片验证码（MOCK 实现）。
 * <p>
 * 此为课程设计级 mock 实现：
 * - 验证码同时以明文返回（captchaCode），仅用于开发调试
 * - 生产环境应移除 captchaCode 字段
 * - 图片验证码使用 AWT BufferedImage 绘制，无头环境可能不可用
 */
@RestController
@RequestMapping("/api/v1/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_MINUTES = 5;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int IMAGE_WIDTH = 160;
    private static final int IMAGE_HEIGHT = 50;
    private static final int FONT_SIZE = 28;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * GET /api/v1/captcha
     * 生成验证码图片，返回 captchaId 和 base64 图片数据。
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateCode();

        // 存储到 Redis
        String key = CAPTCHA_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, code, CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);

        // 生成图片（可能因为无头环境失败）
        String imageBase64 = generateCaptchaImage(code);

        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("captchaId", captchaId);
        result.put("captchaCode", code); // 调试用——前端可直接显示文字

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            result.put("image", "data:image/png;base64," + imageBase64);
        } else {
            // AWT 不可用时返回纯文本验证码
            result.put("image", "");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/captcha/{captchaId}/image
     * 获取指定验证码的图片（备用端点，直接返回 PNG）。
     */
    @GetMapping("/{captchaId}/image")
    public ResponseEntity<byte[]> getCaptchaImage(@PathVariable String captchaId) {
        String storedCode = (String) redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
        if (storedCode == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            String base64 = generateCaptchaImage(storedCode);
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(imageBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String generateCaptchaImage(String code) {
        try {
            BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            // 白色背景
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 浅灰色干扰线（少量）
            g.setColor(Color.LIGHT_GRAY);
            Random rand = new Random();
            for (int i = 0; i < 3; i++) {
                int x1 = rand.nextInt(IMAGE_WIDTH);
                int y1 = rand.nextInt(IMAGE_HEIGHT);
                int x2 = rand.nextInt(IMAGE_WIDTH);
                int y2 = rand.nextInt(IMAGE_HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 绘制验证码文字
            g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));
            FontMetrics fm = g.getFontMetrics();
            int totalWidth = 0;
            for (int i = 0; i < code.length(); i++) {
                totalWidth += fm.charWidth(code.charAt(i));
            }
            int x = (IMAGE_WIDTH - totalWidth) / 2;
            int y = (IMAGE_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();

            for (int i = 0; i < code.length(); i++) {
                // 每个字符随机颜色（深色）
                Color c = new Color(
                        30 + rand.nextInt(100),
                        30 + rand.nextInt(100),
                        30 + rand.nextInt(100)
                );
                g.setColor(c);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                x += fm.charWidth(code.charAt(i));
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            // 降级：返回空字符串
            return "";
        }
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