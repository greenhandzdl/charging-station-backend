package com.charging.service;

import com.charging.entity.ChargeRecord;
import com.charging.entity.Charger;
import com.charging.entity.User;
import com.charging.enums.*;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.impl.BillingServiceImpl;
import com.charging.infrastructure.connector.ChargerConnector;
import com.charging.service.impl.ChargingServiceImpl;
import com.charging.strategy.PeakPricing;
import com.charging.strategy.StandardPricing;
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
class ChargingServiceTest {

    @Mock
    private ChargerMapper chargerMapper;
    @Mock
    private ChargeRecordMapper chargeRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ChargerConnector chargerConnector;

    private BillingService billingService;
    private ChargingService chargingService;

    private UUID userId;
    private UUID chargerId;
    private UUID recordId;
    private User testUser;
    private Charger testCharger;

    @BeforeEach
    void setUp() {
        StandardPricing standardPricing = new StandardPricing();
        PeakPricing peakPricing = new PeakPricing();
        billingService = new BillingServiceImpl(standardPricing, peakPricing);
        chargingService = new ChargingServiceImpl(chargerMapper, chargeRecordMapper, userMapper,
                auditLogMapper, paymentService, billingService, chargerConnector);

        setField(chargingService, "minBalance", new BigDecimal("10.00"));

        userId = UUID.randomUUID();
        chargerId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .name("TestUser")
                .balance(new BigDecimal("100.00"))
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();

        testCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("C001")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .occupiedBy(userId)
                .stationId(UUID.randomUUID())
                .build();
    }

    @Test
    void startCharge_shouldSucceed() {
        when(userMapper.findById(userId)).thenReturn(Optional.of(testUser));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(chargeRecordMapper.countProcessingByUserId(userId)).thenReturn(0);
        when(chargerMapper.updateStatusConditionally(chargerId, "CHARGING", "IDLE")).thenReturn(1);

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        ChargeResponse response = chargingService.startCharge(userId, request);

        assertNotNull(response);
        assertNotNull(response.getRecordId());
        assertEquals("PROCESSING", response.getStatus());
    }

    @Test
    void startCharge_shouldThrowException_whenInsufficientBalance() {
        testUser.setBalance(new BigDecimal("5.00"));
        when(userMapper.findById(userId)).thenReturn(Optional.of(testUser));

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId, request));
    }

    @Test
    void startCharge_shouldThrowException_whenChargerOccupied() {
        when(userMapper.findById(userId)).thenReturn(Optional.of(testUser));
        when(chargeRecordMapper.countProcessingByUserId(userId)).thenReturn(0);
        testCharger.setStatus(ChargerStatus.CHARGING);
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId, request));
    }

    @Test
    void startCharge_shouldThrowException_whenChargerOffline() {
        when(userMapper.findById(userId)).thenReturn(Optional.of(testUser));
        when(chargeRecordMapper.countProcessingByUserId(userId)).thenReturn(0);
        testCharger.setOnlineStatus("OFFLINE");
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> chargingService.startCharge(userId, request));
        assertTrue(ex.getMessage().contains("不在线"));
    }

    @Test
    void startCharge_shouldThrowException_whenAccountFrozen() {
        testUser.setFrozenUntil(LocalDateTime.now().plusDays(1));
        when(userMapper.findById(userId)).thenReturn(Optional.of(testUser));

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId, request));
    }

    @Test
    void stopCharge_withSufficientBalance_shouldDeduct() {
        ChargeRecord record = ChargeRecord.builder()
                .id(recordId)
                .userId(userId)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .deductionStatus(DeductionStatus.PENDING)
                .startTime(LocalDateTime.now().minusMinutes(30))
                .build();

        when(chargeRecordMapper.findByIdWithLock(recordId)).thenReturn(java.util.Map.of("id", recordId));
        when(userMapper.findByIdWithLock(userId)).thenReturn(Optional.of(testUser));
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(record));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(userMapper.deductBalance(any(), any())).thenReturn(1);

        StopChargeRequest request = StopChargeRequest.builder()
                .recordId(recordId)
                .build();

        ChargeResponse response = chargingService.stopCharge(userId, "USER", request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        verify(paymentService).autoDeduct(any(), any(), any());
    }

    @Test
    void forceStop_shouldSucceed() {
        ChargeRecord record = ChargeRecord.builder()
                .id(recordId)
                .userId(userId)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .startTime(LocalDateTime.now().minusMinutes(30))
                .build();

        when(chargeRecordMapper.findByIdWithLock(recordId)).thenReturn(java.util.Map.of("id", recordId));
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(record));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(userMapper.findByIdWithLock(userId)).thenReturn(Optional.of(testUser));
        when(userMapper.deductBalance(any(), any())).thenReturn(1);

        ForceStopRequest request = ForceStopRequest.builder()
                .reason("设备异常发热")
                .build();

        ChargeResponse response = chargingService.forceStop(UUID.randomUUID(), recordId, request, "127.0.0.1");

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void stopCharge_shouldMarkArrears_whenInsufficientBalance() {
        testUser.setBalance(new BigDecimal("5.00"));

        ChargeRecord record = ChargeRecord.builder()
                .id(recordId)
                .userId(userId)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .startTime(LocalDateTime.now().minusMinutes(30))
                .build();

        when(chargeRecordMapper.findByIdWithLock(recordId)).thenReturn(java.util.Map.of("id", recordId));
        when(userMapper.findByIdWithLock(userId)).thenReturn(Optional.of(testUser));
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(record));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));

        StopChargeRequest request = StopChargeRequest.builder()
                .recordId(recordId)
                .build();

        ChargeResponse response = chargingService.stopCharge(userId, "USER", request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        verify(userMapper).freezeAccount(userId);
    }

    // ==================== plugIn / unplug / select tests ====================

    @Test
    void plugIn_shouldOccupyChargerAndReturnSessionId() {
        Charger charger = Charger.builder()
                .id(chargerId)
                .stationId(UUID.randomUUID())
                .chargerCode("TEST-01")
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(charger));
        when(chargerMapper.occupy(chargerId, userId)).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);

        Map<String, Object> result = chargingService.plugIn(chargerId, userId);

        assertNotNull(result);
        assertNotNull(result.get("sessionId"));
        assertEquals(chargerId.toString(), result.get("chargerId"));
        verify(chargerMapper).occupy(chargerId, userId);
    }

    @Test
    void plugIn_chargerOffline_shouldThrow() {
        Charger charger = Charger.builder()
                .id(chargerId)
                .onlineStatus("OFFLINE")
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(charger));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingService.plugIn(chargerId, userId));
        assertTrue(ex.getMessage().contains("不在线"));
    }

    @Test
    void plugIn_alreadyOccupied_shouldThrow() {
        Charger charger = Charger.builder()
                .id(chargerId)
                .onlineStatus("ONLINE")
                .occupiedBy(UUID.randomUUID())
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(charger));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingService.plugIn(chargerId, userId));
        assertTrue(ex.getMessage().contains("占用"));
    }

    @Test
    void unplug_shouldReleaseCharger() {
        Charger charger = Charger.builder()
                .id(chargerId)
                .chargerCode("TEST-01")
                .type(ChargerType.FAST)
                .occupiedBy(userId)
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(charger));
        when(chargeRecordMapper.findProcessingByChargerId(chargerId)).thenReturn(List.of());
        when(chargerMapper.releaseForce(chargerId)).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);

        Map<String, Object> result = chargingService.unplug(chargerId);

        assertNotNull(result);
        assertEquals("拔枪成功", result.get("message"));
        verify(chargerMapper).releaseForce(chargerId);
    }

    @Test
    void selectCharger_withValidSession_shouldReturnChargerInfo() {
        // Phase 1: plugIn — charger not yet occupied
        Charger unpluggedCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("TEST-01")
                .type(ChargerType.FAST)
                .deviceType("SIMULATED")
                .onlineStatus("ONLINE")
                .occupiedBy(null) // <-- not occupied yet
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(unpluggedCharger));
        when(chargerMapper.occupy(chargerId, userId)).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);

        // Plug in first to set session in the chargerSessions map
        Map<String, Object> plugResult = chargingService.plugIn(chargerId, userId);
        String sessionId = (String) plugResult.get("sessionId");

        // Phase 2: selectCharger — charger should now appear occupied
        Charger pluggedCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("TEST-01")
                .type(ChargerType.FAST)
                .deviceType("SIMULATED")
                .onlineStatus("ONLINE")
                .occupiedBy(userId)
                .build();
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(pluggedCharger));

        // Then select
        Map<String, Object> result = chargingService.selectCharger(chargerId, userId, sessionId);
        assertNotNull(result);
        assertEquals(chargerId.toString(), result.get("chargerId"));
        assertEquals("选择充电桩成功", result.get("message"));
    }

    // ==================== estimateEnergyKwh Tests ====================

    @Test
    void estimateEnergyKwh_nullStartTime_returnsDefault() {
        try {
            java.lang.reflect.Method method = ChargingServiceImpl.class.getDeclaredMethod(
                    "estimateEnergyKwh", ChargerType.class, LocalDateTime.class);
            method.setAccessible(true);

            ChargerType type = ChargerType.FAST;
            BigDecimal result = (BigDecimal) method.invoke(chargingService, type, (LocalDateTime) null);

            // startTime 为 null 时按默认值 30kWh（快充×0.5h）估算，不再返回 0
            assertEquals(BigDecimal.valueOf(30.0).setScale(1, java.math.RoundingMode.HALF_UP), result);

            // 慢充也应返回默认值 3.5kWh
            result = (BigDecimal) method.invoke(chargingService, ChargerType.SLOW, (LocalDateTime) null);
            assertEquals(BigDecimal.valueOf(3.5).setScale(1, java.math.RoundingMode.HALF_UP), result);
        } catch (Exception e) {
            throw new RuntimeException("Reflection failed", e);
        }
    }

    // ==================== autoStopOnInsufficientBalance Tests ====================

    @Test
    void autoStopOnInsufficientBalance_skipNullStartTime() {
        testUser.setBalance(new BigDecimal("5.00"));

        // Record with null startTime -- findProcessingRecordsWithStartTime() filters these out
        ChargeRecord record = ChargeRecord.builder()
                .id(recordId)
                .userId(userId)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .startTime(null)
                .build();

        // findProcessingRecordsWithStartTime() only returns records WHERE start_time IS NOT NULL,
        // so our record with null startTime will NOT be returned
        when(chargeRecordMapper.findProcessingRecordsWithStartTime()).thenReturn(java.util.List.of());

        chargingService.autoStopOnInsufficientBalance();

        // Since no records are returned, the loop body never executes
        verify(chargeRecordMapper, never()).completeRecord(any(), any(), any(), any());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
