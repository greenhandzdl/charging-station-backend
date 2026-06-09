package com.charging.infrastructure.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的短信服务实现。
 * <p>
 * 验证码通过日志输出（课程设计级别），生产环境应替换为真实短信通道。
 * 验证码存储在 Redis，5 分钟过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSmsService implements SmsService {

    private static final String SMS_CODE_PREFIX = "sms_code:";
    private static final long CODE_TTL_SECONDS = 300; // 5 minutes

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void sendVerificationCode(String phone, String code) {
        redisTemplate.opsForValue().set(
                SMS_CODE_PREFIX + phone, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("[SMS] 验证码发送 to {}: {}（5分钟内有效）", phone, code);
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String key = SMS_CODE_PREFIX + phone;
        String stored = (String) redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return false;
        }
        boolean match = stored.equals(code);
        if (match) {
            redisTemplate.delete(key);
        }
        return match;
    }

    @Override
    public long getCodeTtl(String phone) {
        Long ttl = redisTemplate.getExpire(SMS_CODE_PREFIX + phone);
        return ttl != null ? ttl : 0L;
    }
}