package com.charging.service.impl;

import com.charging.infrastructure.dto.CaptchaResult;
import com.charging.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RedisCaptchaService —— 基于 Redis 的图片验证码服务（MOCK 实现）。
 * <p>
 * 此为课程设计级 mock 实现：
 * - 验证码同时以明文返回（captchaCode），仅用于开发调试
 * - 生产环境应移除 captchaCode 字段
 * - 图片验证码使用 AWT BufferedImage 绘制，无头环境可能不可用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCaptchaService implements CaptchaService {

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_MINUTES = 5;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int IMAGE_WIDTH = 160;
    private static final int IMAGE_HEIGHT = 50;
    private static final int FONT_SIZE = 28;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public CaptchaResult generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();
        String code = generateCode();

        // 存储到 Redis
        String key = CAPTCHA_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, code, CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);

        // 生成图片（可能因为无头环境失败）
        String imageBase64 = generateCaptchaImage(code);

        String image = (imageBase64 != null && !imageBase64.isEmpty())
                ? "data:image/png;base64," + imageBase64
                : "";

        return CaptchaResult.builder()
                .captchaId(captchaId)
                .captchaCode(code)
                .image(image)
                .build();
    }

    @Override
    public boolean validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null || captchaId.isBlank() || captchaCode.isBlank()) {
            return false;
        }
        String key = CAPTCHA_PREFIX + captchaId;
        String stored = (String) redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equalsIgnoreCase(captchaCode)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String generateCaptchaImage(String code) {
        try {
            BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();

            // 白色背景
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // 深色干扰线（少量）
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
            log.warn("AWT not available in headless environment, returning empty image", e);
            // 降级：返回空字符串
            return "";
        }
    }
}