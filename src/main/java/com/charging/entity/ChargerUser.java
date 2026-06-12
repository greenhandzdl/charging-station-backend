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
    private String loginId;          // 登录账号
    private String name;
    private String passwordHash;
    private String permissionLevel;  // CHARGER, STATION, STATION_GLOBAL
    private UUID chargerId;          // CHARGER 级别绑定的充电桩
    private UUID stationId;          // STATION 级别管理的充电站
    private UUID parentId;           // 上级身份 ID
    private Integer tokenVersion;    // token 版本号
    private Boolean isActive;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}