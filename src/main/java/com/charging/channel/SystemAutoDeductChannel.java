package com.charging.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 系统自动扣费通道 - 用于自动扣费场景（非外部支付网关）
 */
@Slf4j
@Component
public class SystemAutoDeductChannel implements PaymentChannel {

    @Override
    public boolean processPayment(BigDecimal amount) {
        log.info("Processing system auto-deduct: amount={}", amount);
        // System internal deduction, always succeeds at channel level
        return true;
    }

    @Override
    public boolean verifySignature(String payload, String signature) {
        // System internal operations don't need signature verification
        return true;
    }
}