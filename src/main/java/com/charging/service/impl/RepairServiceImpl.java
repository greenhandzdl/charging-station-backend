package com.charging.service.impl;

import com.charging.entity.AuditLog;
import com.charging.entity.Repair;
import com.charging.enums.ChargerStatus;
import com.charging.enums.RepairStatus;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.RepairMapper;
import com.charging.service.ChargerService;
import com.charging.service.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairServiceImpl implements RepairService {

    private final RepairMapper repairMapper;
    private final ChargerMapper chargerMapper;
    private final AuditLogMapper auditLogMapper;
    private final ChargerService chargerService;

    @Override
    @Transactional
    public RepairResponse submit(UUID userId, SubmitRepairRequest request) {
        // Create repair
        Repair repair = Repair.builder()
                .id(UUID.randomUUID())
                .chargerId(request.getChargerId())
                .reporterId(userId)
                .description(request.getDescription())
                .status(RepairStatus.OPEN)
                .reportedAt(LocalDateTime.now())
                .build();

        repairMapper.insert(repair);

        // Update charger status to fault (only if currently idle or fault)
        chargerMapper.updateStatusConditionally(request.getChargerId(), "fault", "idle");

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("submit_repair")
                .resource("charger")
                .resourceId(request.getChargerId())
                .build());

        return RepairResponse.builder()
                .id(repair.getId())
                .status("open")
                .build();
    }

    @Override
    public List<RepairResponse> listRepairs(UUID userId, String userRole, Map<String, String> params) {
        boolean isAdminOrMaintainer = "ADMIN".equals(userRole)
                || "SUPER_ADMIN".equals(userRole)
                || "MAINTAINER".equals(userRole);

        List<Repair> repairs;

        String status = params.get("status");
        if (isAdminOrMaintainer) {
            repairs = (status != null) ? repairMapper.findByStatus(status) : repairMapper.findAll();
        } else {
            repairs = repairMapper.findByReporterId(userId);
            if (status != null) {
                repairs.removeIf(r -> !r.getStatus().name().equalsIgnoreCase(status));
            }
        }

        List<RepairResponse> responses = new ArrayList<>();
        for (Repair r : repairs) {
            responses.add(RepairResponse.builder()
                    .id(r.getId())
                    .chargerId(r.getChargerId())
                    .reporterId(r.getReporterId())
                    .description(r.getDescription())
                    .status(r.getStatus().name().toLowerCase())
                    .handledBy(r.getHandledBy())
                    .reportedAt(r.getReportedAt())
                    .handledAt(r.getHandledAt())
                    .rejectReason(r.getRejectReason())
                    .build());
        }
        return responses;
    }

    @Override
    @Transactional
    public void assign(UUID repairId, AssignRepairRequest request, UUID adminId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.OPEN) {
            throw BusinessException.conflict("只有OPEN状态的报修单可分配");
        }

        repairMapper.assign(repairId, request.getHandledBy());

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(adminId)
                .actorType("admin")
                .action("assign_repair")
                .resource("repair")
                .resourceId(repairId)
                .build());
    }

    @Override
    @Transactional
    public void resolve(UUID repairId, UUID userId, String userRole) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.IN_PROGRESS) {
            throw BusinessException.conflict("只有IN_PROGRESS状态的报修单可标记完成");
        }

        // MAINTAINER can only resolve their own assigned repairs
        if ("MAINTAINER".equals(userRole) && !userId.equals(repair.getHandledBy())) {
            throw BusinessException.forbidden("维修人员只能处理自己被分配的报修");
        }

        repairMapper.resolve(repairId);

        String actorType = "MAINTAINER".equals(userRole) ? "maintainer" : "admin";
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType(actorType)
                .action("resolve_repair")
                .resource("repair")
                .resourceId(repairId)
                .build());
    }

    @Override
    @Transactional
    public void close(UUID repairId, UUID adminId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.OPEN && repair.getStatus() != RepairStatus.RESOLVED) {
            throw BusinessException.conflict("只有OPEN或RESOLVED状态的报修单可关闭");
        }

        boolean wasOpen = repair.getStatus() == RepairStatus.OPEN;
        repairMapper.close(repairId);

        String action = wasOpen ? "close_repair_direct" : "close_repair";

        if (!wasOpen) {
            // Restore charger to idle
            chargerService.restoreCharger(repair.getChargerId());
        }

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(adminId)
                .actorType("admin")
                .action(action)
                .resource("repair")
                .resourceId(repairId)
                .build());
    }

    @Override
    @Transactional
    public void reject(UUID repairId, RejectRepairRequest request, UUID adminId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.RESOLVED) {
            throw BusinessException.conflict("只有RESOLVED状态的报修单可退回");
        }

        repairMapper.reject(repairId, request.getReason());

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(adminId)
                .actorType("admin")
                .action("reject_repair")
                .resource("repair")
                .resourceId(repairId)
                .payload("{\"reason\": \"" + request.getReason() + "\"}")
                .build());
    }
}