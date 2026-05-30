package com.charging.service;

import com.charging.enums.ChargerStatus;
import com.charging.exception.BusinessException;
import com.charging.mapper.ChargerMapper;
import com.charging.service.impl.ChargerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargerServiceTest {

    @Mock
    private ChargerMapper chargerMapper;

    private ChargerService chargerService;
    private UUID chargerId;

    @BeforeEach
    void setUp() {
        chargerService = new ChargerServiceImpl(chargerMapper);
        chargerId = UUID.randomUUID();
    }

    @Test
    void restoreCharger_shouldUpdateToIdle() {
        when(chargerMapper.updateStatusConditionally(chargerId, "idle", "fault")).thenReturn(1);

        chargerService.restoreCharger(chargerId);

        verify(chargerMapper).updateStatusConditionally(chargerId, "idle", "fault");
    }

    @Test
    void getChargerStatus_shouldReturnStatus() {
        when(chargerMapper.findStatusById(chargerId)).thenReturn("idle");

        ChargerStatus status = chargerService.getChargerStatus(chargerId);

        assertEquals(ChargerStatus.IDLE, status);
    }

    @Test
    void getChargerStatus_shouldThrowException_whenNotFound() {
        when(chargerMapper.findStatusById(chargerId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> chargerService.getChargerStatus(chargerId));
    }

    @Test
    void updateStatus_shouldUpdate() {
        chargerService.updateStatus(chargerId, ChargerStatus.FAULT);

        verify(chargerMapper).updateStatus(chargerId, ChargerStatus.FAULT);
    }
}