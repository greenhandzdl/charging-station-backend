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
public class ChargerUser {
    private UUID id;
    private UUID chargerId;         // NULL for GLOBAL identity
    private String name;
    private String phone;
    private String passwordHash;
    private String identityType;    // SINGLE or GLOBAL
    private Boolean isActive;
    private String allowedChargerIds; // GLOBAL accessible charger IDs (JSON array)
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}