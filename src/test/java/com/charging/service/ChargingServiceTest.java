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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

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

        // Set minBalance via reflection
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