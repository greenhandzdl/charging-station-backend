package com.charging.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
public class WeChatPayChannel implements PaymentChannel {

    @Value("${payment.wechat-api-key:wechat-default-key}")
    private String apiKey;

    @Override
    public boolean processPayment(BigDecimal amount) {
        log.info("Processing WeChat payment: amount={}", amount);
        // Mock implementation - always returns true
        return true;
    }

    @Override
    public boolean verifySignature(String payload, String signature) {
        try {
            String expected = hmacSha256(payload, apiKey);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("WeChat signature verification failed", e);
            return false;
        }
    }

    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}