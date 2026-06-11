package com.charging.controller;

import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.RepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairControllerTest {

    @Mock
    private RepairService repairService;

    private RepairController repairController;

    private UUID userId;
    private UUID adminId;
    private JwtUserPrincipal userPrincipal;
    private JwtUserPrincipal adminPrincipal;
    private JwtUserPrincipal maintainerPrincipal;

    @BeforeEach
    void setUp() {
        repairController = new RepairController(repairService);

        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();
        adminPrincipal = JwtUserPrincipal.builder()
                .userId(adminId.toString())
                .role("ADMIN")
                .build();
        maintainerPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("MAINTAINER")
                .build();
    }

    // ==================== submitRepair ====================

    @Test
    void submitRepair_shouldReturn201() {
        UUID chargerId = UUID.randomUUID();
        SubmitRepairRequest request = SubmitRepairRequest.builder()
                .chargerId(chargerId)
                .description("充电桩无法启动")
                .build();
        RepairResponse expectedResponse = RepairResponse.builder()
                .id(UUID.randomUUID())
                .chargerId(chargerId)
                .description("充电桩无法启动")
                .status("OPEN")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repairService.submit(userId, request)).thenReturn(expectedResponse);

        ResponseEntity<RepairResponse> response = repairController.submitRepair(request, userPrincipal);

        assertEquals(HttpStatus.CREATED.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(expectedResponse.getId(), response.getBody().getId());
        assertEquals("OPEN", response.getBody().getStatus());
        verify(repairService).submit(userId, request);
    }

    @Test
    void submitRepair_withInvalidCharger_shouldThrowBusinessException() {
        UUID chargerId = UUID.randomUUID();
        SubmitRepairRequest request = SubmitRepairRequest.builder()
                .chargerId(chargerId)
                .description("充电桩无法启动")
                .build();

        when(repairService.submit(userId, request))
                .thenThrow(BusinessException.notFound("Charger", chargerId.toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.submitRepair(request, userPrincipal));
        assertEquals("NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("Charger not found"));
        verify(repairService).submit(userId, request);
    }

    // ==================== listRepairs ====================

    @Test
    void listRepairs_shouldReturn200WithList() {
        Map<String, String> params = Map.of("status", "OPEN");
        RepairResponse repair1 = RepairResponse.builder()
                .id(UUID.randomUUID())
                .description("故障A")
                .status("OPEN")
                .build();
        RepairResponse repair2 = RepairResponse.builder()
                .id(UUID.randomUUID())
                .description("故障B")
                .status("OPEN")
                .build();
        List<RepairResponse> expectedRepairs = List.of(repair1, repair2);

        when(repairService.listRepairs(userId, "USER", params)).thenReturn(expectedRepairs);

        ResponseEntity<List<RepairResponse>> response = repairController.listRepairs(params, userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("OPEN", response.getBody().get(0).getStatus());
        verify(repairService).listRepairs(userId, "USER", params);
    }

    @Test
    void listRepairs_withEmptyResult_shouldReturnEmptyList() {
        Map<String, String> params = Map.of("status", "RESOLVED");

        when(repairService.listRepairs(userId, "USER", params)).thenReturn(List.of());

        ResponseEntity<List<RepairResponse>> response = repairController.listRepairs(params, userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
        verify(repairService).listRepairs(userId, "USER", params);
    }

    // ==================== assignRepair ====================

    @Test
    void assignRepair_shouldReturn200() {
        UUID repairId = UUID.randomUUID();
        AssignRepairRequest request = AssignRepairRequest.builder()
                .handledBy(UUID.randomUUID())
                .build();

        doNothing().when(repairService).assign(repairId, request, adminId);

        ResponseEntity<Map<String, String>> response = repairController.assignRepair(repairId, request, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("in_progress", response.getBody().get("status"));
        verify(repairService).assign(repairId, request, adminId);
    }

    @Test
    void assignRepair_whenAlreadyAssigned_shouldThrowBusinessException() {
        UUID repairId = UUID.randomUUID();
        AssignRepairRequest request = AssignRepairRequest.builder()
                .handledBy(UUID.randomUUID())
                .build();

        doThrow(BusinessException.conflict("该维修单已被接单"))
                .when(repairService).assign(repairId, request, adminId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.assignRepair(repairId, request, adminPrincipal));
        assertEquals("CONFLICT", ex.getCode());
        verify(repairService).assign(repairId, request, adminId);
    }

    // ==================== claimRepair ====================

    @Test
    void claimRepair_shouldReturn200() {
        UUID repairId = UUID.randomUUID();

        doNothing().when(repairService).claim(repairId, userId);

        ResponseEntity<Map<String, String>> response = repairController.claimRepair(repairId, maintainerPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("in_progress", response.getBody().get("status"));
        verify(repairService).claim(repairId, userId);
    }

    @Test
    void claimRepair_whenAlreadyClaimed_shouldThrowBusinessException() {
        UUID repairId = UUID.randomUUID();

        doThrow(BusinessException.conflict("该维修单已被其他人员接单"))
                .when(repairService).claim(repairId, userId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.claimRepair(repairId, maintainerPrincipal));
        assertEquals("CONFLICT", ex.getCode());
        verify(repairService).claim(repairId, userId);
    }

    // ==================== resolveRepair ====================

    @Test
    void resolveRepair_shouldReturn200() {
        UUID repairId = UUID.randomUUID();

        doNothing().when(repairService).resolve(repairId, userId, "MAINTAINER");

        ResponseEntity<Map<String, String>> response = repairController.resolveRepair(repairId, maintainerPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("resolved", response.getBody().get("status"));
        verify(repairService).resolve(repairId, userId, "MAINTAINER");
    }

    @Test
    void resolveRepair_whenNotAssignedMaintainer_shouldThrowBusinessException() {
        UUID repairId = UUID.randomUUID();

        doThrow(BusinessException.forbidden("您不是该维修单的处理人"))
                .when(repairService).resolve(repairId, userId, "MAINTAINER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.resolveRepair(repairId, maintainerPrincipal));
        assertEquals("FORBIDDEN", ex.getCode());
        verify(repairService).resolve(repairId, userId, "MAINTAINER");
    }

    // ==================== closeRepair ====================

    @Test
    void closeRepair_shouldReturn200() {
        UUID repairId = UUID.randomUUID();

        doNothing().when(repairService).close(repairId, adminId);

        ResponseEntity<Map<String, String>> response = repairController.closeRepair(repairId, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("closed", response.getBody().get("status"));
        verify(repairService).close(repairId, adminId);
    }

    @Test
    void closeRepair_whenNotResolved_shouldThrowBusinessException() {
        UUID repairId = UUID.randomUUID();

        doThrow(BusinessException.conflict("只有已解决的维修单才能关闭"))
                .when(repairService).close(repairId, adminId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.closeRepair(repairId, adminPrincipal));
        assertEquals("CONFLICT", ex.getCode());
        verify(repairService).close(repairId, adminId);
    }

    // ==================== rejectRepair ====================

    @Test
    void rejectRepair_withReason_shouldReturn200() {
        UUID repairId = UUID.randomUUID();
        RejectRepairRequest request = RejectRepairRequest.builder()
                .reason("维修不彻底，需重新处理")
                .build();

        doNothing().when(repairService).reject(repairId, request, adminId);

        ResponseEntity<Map<String, String>> response = repairController.rejectRepair(repairId, request, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("in_progress", response.getBody().get("status"));
        verify(repairService).reject(repairId, request, adminId);
    }

    @Test
    void rejectRepair_whenReasonEmpty_shouldThrowBusinessException() {
        UUID repairId = UUID.randomUUID();
        RejectRepairRequest request = RejectRepairRequest.builder()
                .reason("")
                .build();

        doThrow(BusinessException.badRequest("退回原因不能为空"))
                .when(repairService).reject(repairId, request, adminId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> repairController.rejectRepair(repairId, request, adminPrincipal));
        assertEquals("BAD_REQUEST", ex.getCode());
        verify(repairService).reject(repairId, request, adminId);
    }
}
