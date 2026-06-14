package com.charging.service.impl;

import com.charging.entity.AuditLog;
import com.charging.entity.Charger;
import com.charging.entity.Repair;
import com.charging.entity.User;
import com.charging.enums.ChargerStatus;
import com.charging.enums.RepairStatus;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.RepairMapper;
import com.charging.mapper.UserMapper;
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
    private final UserMapper userMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to convert to JSON", e);
            return "{}";
        }
    }

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

        // Update charger status to fault(Only if currently idea or fault)
        chargerMapper.updateStatusConditionally(request.getChargerId(), "FAULT", "IDLE");

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("SUBMIT_REPAIR")
                .resource("charger")
                .resourceId(request.getChargerId())
                .build());

        return RepairResponse.builder()
                .id(repair.getId())
                .status("OPEN")
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
            // Filter out soft-deleted repairs for management views
            repairs.removeIf(r -> r.getStatus() == RepairStatus.DELETED);
        } else {
            repairs = repairMapper.findByReporterId(userId);
            if (status != null) {
                repairs.removeIf(r -> !r.getStatus().name().equalsIgnoreCase(status));
            }
        }

        List<RepairResponse> responses = new ArrayList<>();
        for (Repair r : repairs) {
            String reporterName = null;
            String handlerName = null;
            String chargerCode = null;

            if (r.getReporterId() != null) {
                reporterName = userMapper.findById(r.getReporterId()).map(User::getName).orElse(null);
            }
            if (r.getHandledBy() != null) {
                handlerName = userMapper.findById(r.getHandledBy()).map(User::getName).orElse(null);
            }
            chargerCode = chargerMapper.findById(r.getChargerId()).map(Charger::getChargerCode).orElse(null);

            responses.add(RepairResponse.builder()
                    .id(r.getId())
                    .chargerId(r.getChargerId())
                    .chargerCode(chargerCode)
                    .reporterId(r.getReporterId())
                    .reporterName(reporterName)
                    .description(r.getDescription())
                    .status(r.getStatus().name().toLowerCase())
                    .handledBy(r.getHandledBy())
                    .handlerName(handlerName)
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
                .action("ASSIGN_REPAIR")
                .resource("repair")
                .resourceId(repairId)
                .build());
    }

    @Override
    @Transactional
    public void claim(UUID repairId, UUID userId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.OPEN) {
            throw BusinessException.conflict("只有OPEN状态的报修单可接单");
        }

        repairMapper.assign(repairId, userId);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("maintainer")
                .action("CLAIM_REPAIR")
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
                .action("RESOLVE_REPAIR")
                .resource("repair")
                .resourceId(repairId)
                .build());
    }

    @Override
    @Transactional
    public void close(UUID repairId, UUID adminId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.OPEN
                && repair.getStatus() != RepairStatus.IN_PROGRESS
                && repair.getStatus() != RepairStatus.RESOLVED) {
            throw BusinessException.conflict("只有OPEN、IN_PROGRESS或RESOLVED状态的报修单可关闭");
        }

        boolean isOpen = repair.getStatus() == RepairStatus.OPEN;
        boolean isInProgress = repair.getStatus() == RepairStatus.IN_PROGRESS;
        repairMapper.close(repairId);

        String action;
        if (isOpen) {
            action = "CLOSE_REPAIR_DIRECT";
        } else if (isInProgress) {
            action = "REJECT_REPAIR_IN_PROGRESS";
        } else {
            action = "CLOSE_REPAIR";
        }

        // Restore charger for non-OPEN (i.e. work was already started on it)
        if (!isOpen) {
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
                .action("REJECT_REPAIR")
                .resource("repair")
                .resourceId(repairId)
                .payload(toJson(Map.of("reason", request.getReason())))
                .build());
    }

    @Override
    @Transactional
    public void softDelete(UUID repairId, UUID userId, String userRole) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        // MAINTAINER can only soft-delete their own assigned repairs
        if ("MAINTAINER".equalsIgnoreCase(userRole) && !userId.equals(repair.getHandledBy())) {
            throw BusinessException.forbidden("维修人员只能删除自己被分配的报修");
        }

        repairMapper.softDelete(repairId);

        String actorType = "admin";
        if ("MAINTAINER".equalsIgnoreCase(userRole)) {
            actorType = "maintainer";
        } else if ("USER".equalsIgnoreCase(userRole)) {
            actorType = "user";
        }

        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType(actorType)
                .action("SOFT_DELETE_REPAIR")
                .resource("repair")
                .resourceId(repairId)
                .payload(toJson(Map.of("previousStatus", (repair.getStatus() != null ? repair.getStatus().name() : "null"))))
                .build());
    }

    @Override
    @Transactional
    public void approveDelete(UUID repairId, UUID adminId) {
        Repair repair = repairMapper.findById(repairId)
                .orElseThrow(() -> BusinessException.notFound("Repair", repairId.toString()));

        if (repair.getStatus() != RepairStatus.DELETED) {
            throw BusinessException.conflict("只有DELETED状态的报修单可审批删除");
        }

        repairMapper.hardDelete(repairId);

        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(adminId)
                .actorType("admin")
                .action("APPROVE_DELETE_REPAIR")
                .resource("repair")
                .resourceId(repairId)
                .build());
    }
}