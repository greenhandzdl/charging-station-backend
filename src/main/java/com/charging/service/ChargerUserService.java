package com.charging.service;

import com.charging.entity.ChargerUser;
import com.charging.infrastructure.dto.ChargerLoginRequest;
import com.charging.infrastructure.dto.ChargerLoginResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargerUserService {
    ChargerLoginResponse login(ChargerLoginRequest request);
    ChargerLoginResponse resetToken(UUID chargerUserId, UUID actorId);
    Optional<ChargerUser> findById(UUID id);
    Optional<ChargerUser> findByChargerId(UUID chargerId);
    boolean canAccessCharger(UUID chargerUserId, UUID chargerId);
    String getPermissionLevel(UUID chargerUserId);
    void updateLastLogin(UUID id);
    ChargerUser create(ChargerUser chargerUser);
    List<ChargerUser> findByStationId(UUID stationId);
    List<ChargerUser> findAll();
}