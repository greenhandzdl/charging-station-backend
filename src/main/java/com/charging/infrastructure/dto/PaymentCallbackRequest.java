package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackRequest {

    private String paymentId;
    private String txId;
    private String status;
    private String signature;
    private String amount;
}