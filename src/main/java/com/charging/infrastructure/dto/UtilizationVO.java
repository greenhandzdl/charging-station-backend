package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilizationVO {

    private long idleCount;
    private long chargingCount;
    private long faultCount;
    private double idlePercent;
    private double chargingPercent;
    private double faultPercent;
}