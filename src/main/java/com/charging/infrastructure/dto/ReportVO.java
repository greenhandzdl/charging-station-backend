package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportVO {

    private long totalCharges;
    private BigDecimal totalEnergy;
    private BigDecimal totalRevenue;
    private BigDecimal avgDailyRevenue;
    private long periodDays;
    private Map<String, Object> details;
}