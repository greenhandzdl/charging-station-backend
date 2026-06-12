package com.charging.entity;

import com.charging.enums.ChargerType;
import com.charging.enums.ChargerStatus;
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
public class Charger {
    private UUID id;
    private UUID stationId;
    private String chargerCode;
    private ChargerType type;
    private ChargerStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String onlineStatus;   // ONLINE or OFFLINE, default OFFLINE
    private LocalDateTime lastHeartbeatAt;
    private String deviceType;     // SIMULATED or REAL, default SIMULATED
    private java.math.BigDecimal ratedPowerKw;
    private String manufacturer;
    private String model;
    private UUID occupiedBy;
    private LocalDateTime occupiedAt;
}