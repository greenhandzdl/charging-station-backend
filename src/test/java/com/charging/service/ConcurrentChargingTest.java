package com.charging.service;

import com.charging.entity.ChargeRecord;
import com.charging.entity.Charger;
import com.charging.entity.User;
import com.charging.enums.*;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.StartChargeRequest;
import com.charging.infrastructure.dto.StopChargeRequest;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.UserMapper;
import com.charging.infrastructure.connector.ChargerConnector;
import com.charging.service.impl.BillingServiceImpl;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcurrentChargingTest {

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

    private ChargingService chargingService;
    private UUID chargerId;
    private UUID userId1;
    private UUID userId2;
    private Charger testCharger;

    @BeforeEach
    void setUp() {
        var standardPricing = new StandardPricing();
        var peakPricing = new PeakPricing();
        var billingService = new BillingServiceImpl(standardPricing, peakPricing);
        chargingService = new ChargingServiceImpl(chargerMapper, chargeRecordMapper, userMapper,
                auditLogMapper, paymentService, billingService, chargerConnector);

        setField(chargingService, "minBalance", new BigDecimal("10.00"));

        chargerId = UUID.randomUUID();
        userId1 = UUID.randomUUID();
        userId2 = UUID.randomUUID();

        testCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("C001")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .occupiedBy(userId1)
                .stationId(UUID.randomUUID())
                .build();
    }

    @Test
    void startCharge_sameCharger_twoUsers_secondShouldFail() {
        // User 1 setup
        User user1 = User.builder()
                .id(userId1)
                .balance(new BigDecimal("100.00"))
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();

        // User 2 setup
        User user2 = User.builder()
                .id(userId2)
                .balance(new BigDecimal("100.00"))
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();

        // First call: user1 starts charge, optimistic lock succeeds
        when(userMapper.findById(userId1)).thenReturn(Optional.of(user1));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(chargeRecordMapper.countProcessingByUserId(userId1)).thenReturn(0);
        when(chargerMapper.updateStatusConditionally(chargerId, "CHARGING", "IDLE")).thenReturn(1);

        StartChargeRequest request1 = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();
        var response1 = chargingService.startCharge(userId1, request1);
        assertNotNull(response1);
        assertEquals("PROCESSING", response1.getStatus());

        // Second call: user2 tries same charger, charger is now CHARGING (status changed by user1)
        Charger busyCharger = Charger.builder()
                .id(chargerId)
                .chargerCode("C001")
                .type(ChargerType.FAST)
                .status(ChargerStatus.CHARGING)  // already occupied
                .onlineStatus("ONLINE")
                .occupiedBy(userId1)
                .stationId(UUID.randomUUID())
                .build();
        when(userMapper.findById(userId2)).thenReturn(Optional.of(user2));
        when(chargeRecordMapper.countProcessingByUserId(userId2)).thenReturn(0);
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(busyCharger));

        StartChargeRequest request2 = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();
        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId2, request2));
    }

    @Test
    void startCharge_sameUser_concurrentCharge_shouldFail() {
        User user = User.builder()
                .id(userId1)
                .balance(new BigDecimal("100.00"))
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();
        UUID otherChargerId = UUID.randomUUID();

        // User already has a PROCESSING charge
        when(userMapper.findById(userId1)).thenReturn(Optional.of(user));
        when(chargeRecordMapper.countProcessingByUserId(userId1)).thenReturn(1);

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(otherChargerId)
                .build();
        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId1, request));
    }

    @Test
    void stopCharge_concurrentStop_onlyFirstShouldSucceed() {
        UUID recordId = UUID.randomUUID();
        User user = User.builder()
                .id(userId1)
                .balance(new BigDecimal("100.00"))
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();
        ChargeRecord record = ChargeRecord.builder()
                .id(recordId)
                .userId(userId1)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .deductionStatus(DeductionStatus.PENDING)
                .startTime(LocalDateTime.now().minusMinutes(30))
                .build();

        // First stop succeeds
        when(chargeRecordMapper.findByIdWithLock(recordId)).thenReturn(Map.of("id", recordId));
        when(userMapper.findByIdWithLock(userId1)).thenReturn(Optional.of(user));
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(record));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(userMapper.deductBalance(any(), any())).thenReturn(1);

        StopChargeRequest request = StopChargeRequest.builder()
                .recordId(recordId)
                .build();
        var response = chargingService.stopCharge(userId1, "USER", request);
        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void startCharge_boundaryBalance_shouldSucceed() {
        User user = User.builder()
                .id(userId1)
                .balance(new BigDecimal("10.00"))  // exactly minBalance
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();

        when(userMapper.findById(userId1)).thenReturn(Optional.of(user));
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(chargeRecordMapper.countProcessingByUserId(userId1)).thenReturn(0);
        when(chargerMapper.updateStatusConditionally(chargerId, "CHARGING", "IDLE")).thenReturn(1);

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();
        var response = chargingService.startCharge(userId1, request);
        assertNotNull(response);
        assertEquals("PROCESSING", response.getStatus());
    }

    @Test
    void startCharge_belowBoundaryBalance_shouldFail() {
        User user = User.builder()
                .id(userId1)
                .balance(new BigDecimal("9.99"))  // just below minBalance
                .frozenUntil(null)
                .role(UserRole.USER)
                .build();

        when(userMapper.findById(userId1)).thenReturn(Optional.of(user));

        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();
        assertThrows(BusinessException.class, () -> chargingService.startCharge(userId1, request));
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