package com.charging.service;

import com.charging.infrastructure.dto.PaymentCallbackRequest;
import com.charging.infrastructure.dto.RechargeRequest;
import com.charging.infrastructure.dto.RechargeResponse;
import com.charging.entity.Payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {
    RechargeResponse recharge(UUID userId, RechargeRequest request);
    void processCallback(PaymentCallbackRequest callback, String gatewayKey);
    List<Payment> queryPayments(UUID userId, String userRole);
    void autoDeduct(UUID userId, BigDecimal amount, UUID chargeRecordId);
}