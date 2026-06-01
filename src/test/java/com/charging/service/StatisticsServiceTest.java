package com.charging.service;

import com.charging.entity.Charger;
import com.charging.entity.Station;
import com.charging.enums.ChargerStatus;
import com.charging.enums.ChargerType;
import com.charging.enums.StationStatus;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.PaymentMapper;
import com.charging.mapper.RepairMapper;
import com.charging.mapper.StationMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private ChargeRecordMapper chargeRecordMapper;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private ChargerMapper chargerMapper;
    @Mock
    private StationMapper stationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RepairMapper repairMapper;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsServiceImpl(chargeRecordMapper, paymentMapper,
                chargerMapper, stationMapper, userMapper, repairMapper);
    }

    @Test
    void getChargerUtilization_shouldCalculateCorrectly() {
        UUID stationId = UUID.randomUUID();
        Charger idle = Charger.builder().id(UUID.randomUUID()).stationId(stationId)
                .status(ChargerStatus.IDLE).build();
        Charger charging = Charger.builder().id(UUID.randomUUID()).stationId(stationId)
                .status(ChargerStatus.CHARGING).build();
        Charger fault = Charger.builder().id(UUID.randomUUID()).stationId(stationId)
                .status(ChargerStatus.FAULT).build();

        when(chargerMapper.findAll()).thenReturn(List.of(idle, charging, fault));

        UtilizationVO utilization = statisticsService.getChargerUtilization();

        assertEquals(1, utilization.getIdleCount());
        assertEquals(1, utilization.getChargingCount());
        assertEquals(1, utilization.getFaultCount());
        assertTrue(utilization.getIdlePercent() > 0);
    }

    @Test
    void getFaultChargers_shouldReturnOnlyFaultOnes() {
        UUID stationId = UUID.randomUUID();
        Charger faultCharger = Charger.builder().id(UUID.randomUUID()).stationId(stationId)
                .chargerCode("F001").type(ChargerType.FAST).status(ChargerStatus.FAULT).build();
        Charger normalCharger = Charger.builder().id(UUID.randomUUID()).stationId(stationId)
                .chargerCode("N001").type(ChargerType.SLOW).status(ChargerStatus.IDLE).build();

        when(chargerMapper.findAll()).thenReturn(List.of(faultCharger, normalCharger));
        when(stationMapper.findById(stationId)).thenReturn(Optional.of(
                Station.builder().id(stationId).name("Test Station").build()));

        List<FaultChargerVO> faults = statisticsService.getFaultChargers();

        assertEquals(1, faults.size());
        assertEquals("F001", faults.get(0).getChargerCode());
    }

    @Test
    void exportCsv_shouldGenerateCsvContent() {
        when(chargeRecordMapper.findAll()).thenReturn(List.of());

        byte[] csvData = statisticsService.exportCsv("charges", Map.of());

        assertNotNull(csvData);
        assertTrue(csvData.length > 0);
        String csv = new String(csvData);
        assertTrue(csv.startsWith("ID,UserID,ChargerID"));
    }
}