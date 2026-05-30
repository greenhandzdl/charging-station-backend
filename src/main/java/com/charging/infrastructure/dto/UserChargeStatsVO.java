package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserChargeStatsVO {

    private UUID userId;
    private String userName;
    private String phone;
    private long chargeCount;
    private BigDecimal totalEnergy;
    private BigDecimal totalFee;
}