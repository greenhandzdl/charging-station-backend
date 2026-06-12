package com.charging.service;

import com.charging.entity.ChargerDevice;

import java.util.Optional;
import java.util.UUID;

public interface ChargerDeviceService {
    Optional<ChargerDevice> findByChargerId(UUID chargerId);
    Optional<ChargerDevice> findByAuthToken(String authToken);
    ChargerDevice create(ChargerDevice device);
    void updateLastOnline(UUID id);
}