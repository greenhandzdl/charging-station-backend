package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaultChargerVO {

    private UUID chargerId;
    private String chargerCode;
    private UUID stationId;
    private String stationName;
    private String chargerType;
    private LocalDateTime lastFaultTime;
    private String status;
}