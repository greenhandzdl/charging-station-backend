package com.charging.infrastructure.dto;

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
public class ChargeResponse {

    private UUID recordId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal energyKwh;
    private BigDecimal fee;
    private String status;
    private String deductionStatus;
    private String message;
}