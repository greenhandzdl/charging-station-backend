package com.charging.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WeChatPayChannel} and {@link AliPayChannel}.
 * <p>
 * Validates:
 * <ul>
 *   <li>HMAC-SHA256 signature verification (correct signatures pass)</li>
 *   <li>Tampered signatures are rejected</li>
 *   <li>Different keys produce different signatures</li>
 *   <li>processPayment mock always returns true</li>
 * </ul>
 */
class PaymentChannelTest {

    private WeChatPayChannel wechatChannel;
    private AliPayChannel aliPayChannel;

    @BeforeEach
    void setUp() {
        wechatChannel = new WeChatPayChannel();
        aliPayChannel = new AliPayChannel();

        // Inject known API keys for deterministic testing
        ReflectionTestUtils.setField(wechatChannel, "apiKey", "test-wechat-key-12345");
        ReflectionTestUtils.setField(aliPayChannel, "apiKey", "test-alipay-key-67890");
    }

    // ===== WeChat HMAC-SHA256 =====

    @Test
    void wechat_verifySignature_withCorrectSignature_returnsTrue() {
        String payload = "payment-001:100.00:SUCCESS";
        // Generate correct signature using the channel's own logic
        assertTrue(wechatChannel.verifySignature(payload, computeHmacSha256Hex(payload, "test-wechat-key-12345")),
                "Correct signature should verify");
    }

    @Test
    void wechat_verifySignature_withWrongSignature_returnsFalse() {
        String payload = "payment-001:100.00:SUCCESS";
        assertFalse(wechatChannel.verifySignature(payload, "invalid-signature"),
                "Wrong signature should be rejected");
    }

    @Test
    void wechat_verifySignature_withTamperedPayload_returnsFalse() {
        String payload = "payment-001:100.00:SUCCESS";
        String signature = computeHmacSha256Hex(payload, "test-wechat-key-12345");

        // Same signature but different payload
        assertFalse(wechatChannel.verifySignature("payment-002:200.00:FAILED", signature),
                "Tampered payload with original signature should be rejected");
    }

    @Test
    void wechat_verifySignature_withDifferentKey_returnsFalse() {
        String payload = "payment-001:100.00:SUCCESS";
        // Sign with wrong key
        String signature = computeHmacSha256Hex(payload, "wrong-key");
        assertFalse(wechatChannel.verifySignature(payload, signature),
                "Signature from different key should be rejected");
    }

    @Test
    void wechat_verifySignature_emptyPayload_returnsFalse() {
        assertFalse(wechatChannel.verifySignature("", "some-signature"),
                "Empty payload with any signature should be rejected");
    }

    @Test
    void wechat_processPayment_alwaysReturnsTrue() {
        assertTrue(wechatChannel.processPayment(new BigDecimal("50.00")));
        assertTrue(wechatChannel.processPayment(new BigDecimal("999999.99")));
        assertTrue(wechatChannel.processPayment(BigDecimal.ZERO));
    }

    // ===== AliPay =====

    @Test
    void alipay_verifySignature_withCorrectSignature_returnsTrue() {
        String payload = "alipay-001:200.00:SUCCESS";
        assertTrue(aliPayChannel.verifySignature(payload, computeHmacSha256Base64(payload, "test-alipay-key-67890")),
                "Correct Alipay signature should verify");
    }

    @Test
    void alipay_verifySignature_withWrongSignature_returnsFalse() {
        String payload = "alipay-001:200.00:SUCCESS";
        assertFalse(aliPayChannel.verifySignature(payload, "random-signature"),
                "Wrong Alipay signature should be rejected");
    }

    @Test
    void alipay_verifySignature_withDifferentKey_returnsFalse() {
        String payload = "alipay-001:200.00:SUCCESS";
        String signature = computeHmacSha256Base64(payload, "wrong-key");
        assertFalse(aliPayChannel.verifySignature(payload, signature),
                "Alipay signature from different key should be rejected");
    }

    @Test
    void alipay_processPayment_alwaysReturnsTrue() {
        assertTrue(aliPayChannel.processPayment(new BigDecimal("30.00")));
        assertTrue(aliPayChannel.processPayment(new BigDecimal("0.01")));
    }

    // ===== Edge cases =====

    @Test
    void wechat_verifySignature_nullSignature_returnsFalse() {
        assertFalse(wechatChannel.verifySignature("payload", null));
    }

    @Test
    void wechat_verifySignature_nullPayload_returnsFalse() {
        assertFalse(wechatChannel.verifySignature(null, "sig"));
    }

    @Test
    void alipay_verifySignature_nullSignature_returnsFalse() {
        assertFalse(aliPayChannel.verifySignature("payload", null));
    }

    // ===== Helpers to generate expected signatures =====

    private String computeHmacSha256Hex(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                    new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private String computeHmacSha256Base64(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                    new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}