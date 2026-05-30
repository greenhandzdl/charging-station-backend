package com.charging.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class AliPayChannel implements PaymentChannel {

    @Value("${payment.alipay-api-key:alipay-default-key}")
    private String apiKey;

    @Override
    public boolean processPayment(BigDecimal amount) {
        log.info("Processing AliPay payment: amount={}", amount);
        // Mock implementation - always returns true
        return true;
    }

    @Override
    public boolean verifySignature(String payload, String signature) {
        try {
            // Mock RSA2 verification - simplified for demo
            String expected = sha256WithRsa(payload);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("AliPay signature verification failed", e);
            return false;
        }
    }

    private String sha256WithRsa(String data) throws Exception {
        // Simplified mock: use HMAC-SHA256 with API key as stand-in for RSA2
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}