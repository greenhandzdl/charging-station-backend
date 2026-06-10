package com.charging.infrastructure.scheduler;

import com.charging.entity.Charger;
import com.charging.enums.ChargerType;
import com.charging.enums.ChargerStatus;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargingSchedulerTest {

    @Mock
    private ChargingService chargingService;
    @Mock
    private ChargerMapper chargerMapper;
    @Mock
    private ChargeRecordMapper chargeRecordMapper;

    private ChargingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ChargingScheduler(chargingService, chargerMapper, chargeRecordMapper);
    }

    @Test
    void checkOfflineChargers_shouldMarkOldHeartbeatAsOffline() {
        Charger offlineCharger = Charger.builder()
                .id(UUID.randomUUID())
                .chargerCode("CY-A01")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .lastHeartbeatAt(LocalDateTime.now().minusSeconds(120))
                .build();
        when(chargerMapper.findAll()).thenReturn(List.of(offlineCharger));

        scheduler.checkOfflineChargers();

        verify(chargerMapper).markOffline(offlineCharger.getId());
    }

    @Test
    void checkOfflineChargers_shouldSkipActiveChargers() {
        Charger activeCharger = Charger.builder()
                .id(UUID.randomUUID())
                .chargerCode("CY-A01")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .lastHeartbeatAt(LocalDateTime.now().minusSeconds(10))
                .build();
        when(chargerMapper.findAll()).thenReturn(List.of(activeCharger));

        scheduler.checkOfflineChargers();

        verify(chargerMapper, never()).markOffline(any());
    }

    @Test
    void checkOfflineChargers_shouldHandleNullHeartbeat() {
        Charger nullHeartbeatCharger = Charger.builder()
                .id(UUID.randomUUID())
                .chargerCode("CY-A02")
                .type(ChargerType.SLOW)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .lastHeartbeatAt(null)
                .build();
        when(chargerMapper.findAll()).thenReturn(List.of(nullHeartbeatCharger));

        scheduler.checkOfflineChargers();

        verify(chargerMapper).markOffline(nullHeartbeatCharger.getId());
    }

    @Test
    void checkOfflineChargers_shouldAutoStopProcessingCharges() {
        UUID chargerId = UUID.randomUUID();
        Charger offlineCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("CY-A01")
                .type(ChargerType.FAST)
                .status(ChargerStatus.CHARGING)
                .onlineStatus("ONLINE")
                .lastHeartbeatAt(LocalDateTime.now().minusSeconds(120))
                .build();
        when(chargerMapper.findAll()).thenReturn(List.of(offlineCharger));
        when(chargingService.forceStopByChargerId(chargerId, "CHARGER_OFFLINE")).thenReturn(1);

        scheduler.checkOfflineChargers();

        verify(chargerMapper).markOffline(chargerId);
        verify(chargingService).forceStopByChargerId(chargerId, "CHARGER_OFFLINE");
    }

    @Test
    void checkOfflineChargers_shouldNotMarkAlreadyOffline() {
        Charger alreadyOffline = Charger.builder()
                .id(UUID.randomUUID())
                .chargerCode("CY-A01")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("OFFLINE")
                .lastHeartbeatAt(LocalDateTime.now().minusSeconds(120))
                .build();
        when(chargerMapper.findAll()).thenReturn(List.of(alreadyOffline));

        scheduler.checkOfflineChargers();

        verify(chargerMapper, never()).markOffline(any());
    }

    @Test
    void checkInsufficientBalance_shouldDelegateToService() {
        when(chargingService.autoStopOnInsufficientBalance()).thenReturn(2);

        scheduler.checkInsufficientBalance();

        verify(chargingService).autoStopOnInsufficientBalance();
    }
}