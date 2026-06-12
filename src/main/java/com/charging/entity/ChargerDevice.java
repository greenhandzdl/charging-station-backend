package com.charging.entity;

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
public class ChargerDevice {
    private UUID id;
    private UUID chargerId;
    private String deviceName;
    private String deviceType;     // SIMULATED or REAL
    private String authToken;
    private String serialNumber;
    private String firmwareVersion;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}