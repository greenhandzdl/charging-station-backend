package com.charging.channel;

import java.math.BigDecimal;

/**
 * 支付通道接口
 */
public interface PaymentChannel {
    boolean processPayment(BigDecimal amount);
    boolean verifySignature(String payload, String signature);
}