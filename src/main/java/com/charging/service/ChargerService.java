package com.charging.service;

import com.charging.enums.ChargerStatus;

import java.util.UUID;

public interface ChargerService {
    void restoreCharger(UUID chargerId);
    ChargerStatus getChargerStatus(UUID chargerId);
    void updateStatus(UUID chargerId, ChargerStatus status);
}