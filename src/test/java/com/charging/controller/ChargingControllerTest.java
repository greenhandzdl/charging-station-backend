package com.charging.controller;

import com.charging.entity.Charger;
import com.charging.enums.ChargerStatus;
import com.charging.enums.ChargerType;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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
class ChargingControllerTest {

    @Mock
    private ChargingService chargingService;

    @Mock
    private ChargerMapper chargerMapper;

    @Mock
    private HttpServletRequest httpServletRequest;

    private ChargingController chargingController;

    private UUID userId;
    private UUID chargerId;
    private UUID recordId;
    private JwtUserPrincipal userPrincipal;
    private JwtUserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        chargingController = new ChargingController(chargingService, chargerMapper);

        userId = UUID.randomUUID();
        chargerId = UUID.randomUUID();
        recordId = UUID.randomUUID();

        userPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();

        adminPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("ADMIN")
                .build();
    }

    // ==================== startCharge Tests ====================

    @Test
    void startCharge_withValidRequest_shouldReturn200() {
        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();
        ChargeResponse expectedResponse = ChargeResponse.builder()
                .recordId(recordId)
                .status("PROCESSING")
                .startTime(LocalDateTime.now())
                .build();

        when(chargingService.startCharge(userId, request)).thenReturn(expectedResponse);

        ResponseEntity<ChargeResponse> response = chargingController.startCharge(request, userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(recordId, response.getBody().getRecordId());
        assertEquals("PROCESSING", response.getBody().getStatus());
        verify(chargingService).startCharge(userId, request);
    }

    @Test
    void startCharge_whenChargerOffline_shouldThrowBusinessException() {
        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        when(chargingService.startCharge(userId, request))
                .thenThrow(BusinessException.chargerOffline());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingController.startCharge(request, userPrincipal));
        assertEquals("CHARGER_OFFLINE", ex.getCode());
        verify(chargingService).startCharge(userId, request);
    }

    @Test
    void startCharge_whenInsufficientBalance_shouldThrowBusinessException() {
        StartChargeRequest request = StartChargeRequest.builder()
                .chargerId(chargerId)
                .build();

        when(chargingService.startCharge(userId, request))
                .thenThrow(BusinessException.insufficientBalance(new BigDecimal("5.00"), new BigDecimal("10.00")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingController.startCharge(request, userPrincipal));
        assertEquals("INSUFFICIENT_BALANCE", ex.getCode());
        verify(chargingService).startCharge(userId, request);
    }

    // ==================== stopCharge Tests ====================

    @Test
    void stopCharge_withValidRequest_shouldReturn200() {
        StopChargeRequest request = StopChargeRequest.builder()
                .recordId(recordId)
                .build();
        ChargeResponse expectedResponse = ChargeResponse.builder()
                .recordId(recordId)
                .status("COMPLETED")
                .endTime(LocalDateTime.now())
                .energyKwh(new BigDecimal("15.5"))
                .fee(new BigDecimal("30.00"))
                .build();

        when(chargingService.stopCharge(userId, "USER", request)).thenReturn(expectedResponse);

        ResponseEntity<ChargeResponse> response = chargingController.stopCharge(request, userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().getStatus());
        assertEquals(new BigDecimal("15.5"), response.getBody().getEnergyKwh());
        verify(chargingService).stopCharge(userId, "USER", request);
    }

    @Test
    void stopCharge_whenNotOwner_shouldThrowBusinessException() {
        StopChargeRequest request = StopChargeRequest.builder()
                .recordId(recordId)
                .build();

        when(chargingService.stopCharge(userId, "USER", request))
                .thenThrow(BusinessException.forbidden("非充电发起人无法停止充电"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingController.stopCharge(request, userPrincipal));
        assertEquals("FORBIDDEN", ex.getCode());
        verify(chargingService).stopCharge(userId, "USER", request);
    }

    // ==================== forceStop Tests ====================

    @Test
    void forceStop_asAdmin_shouldReturn200() {
        ForceStopRequest request = ForceStopRequest.builder()
                .reason("设备异常发热")
                .build();
        ChargeResponse expectedResponse = ChargeResponse.builder()
                .recordId(recordId)
                .status("COMPLETED")
                .message("管理员强制终止")
                .build();

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(chargingService.forceStop(userId, recordId, request, "127.0.0.1"))
                .thenReturn(expectedResponse);

        ResponseEntity<ChargeResponse> response = chargingController.forceStop(
                recordId, request, adminPrincipal, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().getStatus());
        verify(chargingService).forceStop(userId, recordId, request, "127.0.0.1");
    }

    @Test
    void forceStop_withNonExistentRecord_shouldThrowBusinessException() {
        ForceStopRequest request = ForceStopRequest.builder()
                .reason("测试强制停止")
                .build();

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(chargingService.forceStop(userId, recordId, request, "127.0.0.1"))
                .thenThrow(BusinessException.notFound("ChargeRecord", recordId.toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chargingController.forceStop(recordId, request, adminPrincipal, httpServletRequest));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(chargingService).forceStop(userId, recordId, request, "127.0.0.1");
    }

    // ==================== queryCharges Tests ====================

    @Test
    void queryCharges_shouldReturn200WithList() {
        Map<String, String> params = Map.of("page", "1", "size", "10");
        List<Map<String, Object>> expectedRecords = List.of(
                Map.of("id", recordId.toString(), "status", "COMPLETED"),
                Map.of("id", UUID.randomUUID().toString(), "status", "PROCESSING")
        );

        when(chargingService.queryCharges(userId, "USER", params)).thenReturn(expectedRecords);

        ResponseEntity<List<Map<String, Object>>> response = chargingController.queryCharges(params, userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        verify(chargingService).queryCharges(userId, "USER", params);
    }

    @Test
    void queryCharges_adminSeesAll_shouldReturn200() {
        Map<String, String> params = Map.of("page", "1");
        List<Map<String, Object>> expectedRecords = List.of(
                Map.of("id", UUID.randomUUID().toString(), "status", "COMPLETED")
        );

        when(chargingService.queryCharges(userId, "ADMIN", params)).thenReturn(expectedRecords);

        ResponseEntity<List<Map<String, Object>>> response = chargingController.queryCharges(params, adminPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        verify(chargingService).queryCharges(userId, "ADMIN", params);
    }

    // ==================== plugIn / unplug / select Tests ====================

    @Test
    void plugIn_withValidChargerId_shouldReturn200() {
        Map<String, Object> result = Map.of("chargerId", chargerId.toString(), "sessionId", "mock-session", "message", "插枪成功");
        when(chargingService.plugIn(chargerId, userId)).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = chargingController.plugIn(chargerId, userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("插枪成功", response.getBody().get("message"));
        verify(chargingService).plugIn(chargerId, userId);
    }

    @Test
    void unplug_withValidChargerId_shouldReturn200() {
        Map<String, Object> result = Map.of("chargerId", chargerId.toString(), "stoppedRecords", 0, "message", "拔枪成功");
        when(chargingService.unplug(chargerId)).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = chargingController.unplug(chargerId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("拔枪成功", response.getBody().get("message"));
        verify(chargingService).unplug(chargerId);
    }

    @Test
    void selectCharger_withValidSession_shouldReturn200() {
        Map<String, Object> result = Map.of("chargerId", chargerId.toString(), "message", "选择充电桩成功");
        when(chargingService.selectCharger(chargerId, userId, "test-session")).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = chargingController.selectCharger(chargerId, Map.of("sessionId", "test-session"), userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("选择充电桩成功", response.getBody().get("message"));
        verify(chargingService).selectCharger(chargerId, userId, "test-session");
    }

    // ==================== receiveHeartbeat Tests ====================

    @Test
    void receiveHeartbeat_withValidChargerCode_shouldReturn200() {
        Map<String, String> request = Map.of("chargerCode", "C001");
        Charger charger = Charger.builder()
                .id(chargerId)
                .chargerCode("C001")
                .build();

        when(chargerMapper.findByChargerCode("C001")).thenReturn(Optional.of(charger));
        when(chargerMapper.updateHeartbeat(chargerId)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = chargingController.receiveHeartbeat(request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("OK", response.getBody().get("status"));
        assertEquals("C001", response.getBody().get("chargerCode"));
        verify(chargerMapper).findByChargerCode("C001");
        verify(chargerMapper).updateHeartbeat(chargerId);
    }

    @Test
    void receiveHeartbeat_withUnknownCharger_shouldReturn200Ignored() {
        Map<String, String> request = Map.of("chargerCode", "UNKNOWN");

        when(chargerMapper.findByChargerCode("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = chargingController.receiveHeartbeat(request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("IGNORED", response.getBody().get("status"));
        assertEquals("UNKNOWN", response.getBody().get("chargerCode"));
        verify(chargerMapper).findByChargerCode("UNKNOWN");
        verify(chargerMapper, never()).updateHeartbeat(any());
    }

    @Test
    void receiveHeartbeat_withNullChargerCode_shouldReturn400() {
        Map<String, String> request = Map.of();

        ResponseEntity<Map<String, Object>> response = chargingController.receiveHeartbeat(request);

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("chargerCode is required", response.getBody().get("error"));
        verify(chargerMapper, never()).findByChargerCode(any());
    }

    @Test
    void receiveHeartbeat_withEmptyChargerCode_shouldReturn400() {
        Map<String, String> request = Map.of("chargerCode", "");

        ResponseEntity<Map<String, Object>> response = chargingController.receiveHeartbeat(request);

        assertEquals(400, response.getStatusCodeValue());
        assertEquals("chargerCode is required", response.getBody().get("error"));
        verify(chargerMapper, never()).findByChargerCode(any());
    }
}
