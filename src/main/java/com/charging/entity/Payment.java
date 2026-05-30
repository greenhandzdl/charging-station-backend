package com.charging.entity;

import com.charging.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    private UUID id;
    private UUID userId;
    private UUID chargeRecordId;
    private String method;
    private BigDecimal amount;
    private PaymentStatus status;
    private String gatewayTxId;
    private String gatewayCallbackPayload;
    private LocalDateTime createdAt;
}