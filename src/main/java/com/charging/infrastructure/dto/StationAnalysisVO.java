package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationAnalysisVO {

    private String stationId;
    private String stationName;
    private long totalChargers;
    private long idleChargers;
    private long chargingChargers;
    private long faultChargers;
    private long totalCharges;
    private java.math.BigDecimal totalEnergy;
    private java.math.BigDecimal totalRevenue;
    private double utilizationRate;
}