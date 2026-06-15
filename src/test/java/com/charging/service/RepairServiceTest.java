package com.charging.service;

import com.charging.entity.Repair;
import com.charging.enums.RepairStatus;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.RepairMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.impl.RepairServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairServiceTest {

    @Mock
    private RepairMapper repairMapper;
    @Mock
    private ChargerMapper chargerMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private ChargerService chargerService;
    @Mock
    private UserMapper userMapper;

    private RepairService repairService;

    private UUID repairId;
    private UUID chargerId;
    private UUID userId;
    private Repair testRepair;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        repairService = new RepairServiceImpl(repairMapper, chargerMapper, auditLogMapper, chargerService, userMapper, objectMapper);

        repairId = UUID.randomUUID();
        chargerId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testRepair = Repair.builder()
                .id(repairId)
                .chargerId(chargerId)
                .reporterId(userId)
                .description("充电桩故障")
                .status(RepairStatus.OPEN)
                .reportedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void submit_shouldSucceed() {
        SubmitRepairRequest request = SubmitRepairRequest.builder()
                .chargerId(chargerId)
                .description("充电桩无法启动")
                .build();

        RepairResponse response = repairService.submit(userId, request);

        assertNotNull(response);
        assertEquals("OPEN", response.getStatus());
        verify(repairMapper).insert(any(Repair.class));
        verify(chargerMapper).updateStatusConditionally(chargerId, "FAULT", "IDLE");
    }

    @Test
    void assign_shouldSucceed() {
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        AssignRepairRequest request = AssignRepairRequest.builder()
                .handledBy(UUID.randomUUID())
                .build();

        assertDoesNotThrow(() -> repairService.assign(repairId, request, userId));
        verify(repairMapper).assign(repairId, request.getHandledBy());
    }

    @Test
    void assign_shouldThrowException_whenRepairNotOpen() {
        testRepair.setStatus(RepairStatus.IN_PROGRESS);
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        AssignRepairRequest request = AssignRepairRequest.builder()
                .handledBy(UUID.randomUUID())
                .build();

        assertThrows(BusinessException.class, () -> repairService.assign(repairId, request, userId));
    }

    @Test
    void resolve_shouldSucceed() {
        testRepair.setStatus(RepairStatus.IN_PROGRESS);
        testRepair.setHandledBy(userId);
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        assertDoesNotThrow(() -> repairService.resolve(repairId, userId, "MAINTAINER"));
        verify(repairMapper).resolve(repairId);
    }

    @Test
    void resolve_shouldThrowException_whenNotAssignedMaintainer() {
        testRepair.setStatus(RepairStatus.IN_PROGRESS);
        testRepair.setHandledBy(UUID.randomUUID()); // assigned to someone else
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        assertThrows(BusinessException.class,
                () -> repairService.resolve(repairId, userId, "MAINTAINER"));
    }

    @Test
    void close_shouldRestoreCharger_whenResolved() {
        testRepair.setStatus(RepairStatus.RESOLVED);
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        assertDoesNotThrow(() -> repairService.close(repairId, userId));
        verify(chargerService).restoreCharger(chargerId);
        verify(repairMapper).close(repairId);
    }

    @Test
    void reject_shouldSucceed() {
        testRepair.setStatus(RepairStatus.RESOLVED);
        when(repairMapper.findById(repairId)).thenReturn(Optional.of(testRepair));

        RejectRepairRequest request = RejectRepairRequest.builder()
                .reason("维修不彻底，需重新处理")
                .build();

        assertDoesNotThrow(() -> repairService.reject(repairId, request, userId));
        verify(repairMapper).reject(repairId, "维修不彻底，需重新处理");
    }
}