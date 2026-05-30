package com.charging.service.impl;

import com.charging.entity.AuditLog;
import com.charging.entity.ChargeRecord;
import com.charging.entity.Charger;
import com.charging.entity.User;
import com.charging.enums.ChargerStatus;
import com.charging.enums.ChargerType;
import com.charging.enums.DeductionStatus;
import com.charging.enums.RecordStatus;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.ChargeResponse;
import com.charging.infrastructure.dto.ForceStopRequest;
import com.charging.infrastructure.dto.StartChargeRequest;
import com.charging.infrastructure.dto.StopChargeRequest;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.BillingService;
import com.charging.service.ChargingService;
import com.charging.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargingServiceImpl implements ChargingService {

    private final ChargerMapper chargerMapper;
    private final ChargeRecordMapper chargeRecordMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final PaymentService paymentService;
    private final BillingService billingService;

    @Value("${charging.min-balance:10.00}")
    private BigDecimal minBalance;

    @Override
    @Transactional
    public ChargeResponse startCharge(UUID userId, StartChargeRequest request) {
        UUID chargerId = request.getChargerId();

        // Check user exists and is not frozen
        User user = userMapper.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));

        if (user.getFrozenUntil() != null && user.getFrozenUntil().isAfter(LocalDateTime.now())) {
            throw BusinessException.accountFrozen();
        }

        // Check minimum balance
        if (user.getBalance().compareTo(minBalance) < 0) {
            throw BusinessException.insufficientBalance(user.getBalance(), minBalance);
        }

        // Check user has no processing records
        int processingCount = chargeRecordMapper.countProcessingByUserId(userId);
        if (processingCount > 0) {
            throw BusinessException.conflict("用户已有进行中的充电记录");
        }

        // Check charger exists and is idle
        Charger charger = chargerMapper.findById(chargerId)
                .orElseThrow(() -> BusinessException.notFound("Charger", chargerId.toString()));

        if (charger.getStatus() != ChargerStatus.IDLE) {
            throw BusinessException.conflict("充电桩已被占用");
        }

        // Lock charger (optimistic write)
        int updated = chargerMapper.updateStatusConditionally(chargerId, "charging", "idle");
        if (updated == 0) {
            throw BusinessException.conflict("充电桩已被占用");
        }

        // Create charge record
        ChargeRecord record = ChargeRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chargerId(chargerId)
                .startTime(LocalDateTime.now())
                .status(RecordStatus.PROCESSING)
                .deductionStatus(DeductionStatus.PENDING)
                .build();

        chargeRecordMapper.insert(record);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("start_charge")
                .resource("charger")
                .resourceId(chargerId)
                .build());

        return ChargeResponse.builder()
                .recordId(record.getId())
                .startTime(record.getStartTime())
                .status("processing")
                .message("充电已启动")
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ChargeResponse stopCharge(UUID userId, String userRole, StopChargeRequest request) {
        UUID recordId = request.getRecordId();

        // Query record with lock
        Map<String, Object> recordData = chargeRecordMapper.findByIdWithLock(recordId);
        if (recordData == null) {
            throw BusinessException.notFound("ChargeRecord", recordId.toString());
        }

        // Lock user balance explicitly
        User user = userMapper.findByIdWithLock(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));

        ChargeRecord record = chargeRecordMapper.findById(recordId)
                .orElseThrow(() -> BusinessException.notFound("ChargeRecord", recordId.toString()));

        Charger charger = chargerMapper.findById(record.getChargerId())
                .orElseThrow(() -> BusinessException.notFound("Charger", record.getChargerId().toString()));

        // Calculate fee
        BigDecimal energyKwh = new BigDecimal("15.0"); // Mock energy - in real system from simulation
        BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

        boolean balanceSufficient = false;
        DeductionStatus deductionStatus;

        if (user.getBalance().compareTo(fee) >= 0) {
            // Sufficient balance - deduct
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "paid");

            int deductResult = userMapper.deductBalance(userId, fee);
            if (deductResult > 0) {
                balanceSufficient = true;
                deductionStatus = DeductionStatus.PAID;

                // Create payment record
                paymentService.autoDeduct(userId, fee, recordId);

                // Audit log
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(userId)
                        .actorType("user")
                        .action("stop_charge_deducted")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .build());
            } else {
                // Concurrent deduction issue - treat as arrears
                chargeRecordMapper.updateDeductionStatus(recordId, "arrears");
                userMapper.freezeAccount(userId);
                deductionStatus = DeductionStatus.ARREARS;
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(userId)
                        .actorType("system")
                        .action("charge_arrears")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .build());
            }
        } else {
            // Insufficient balance - mark arrears
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "arrears");
            userMapper.freezeAccount(userId);
            deductionStatus = DeductionStatus.ARREARS;

            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(userId)
                    .actorType("user")
                    .action("charge_arrears")
                    .resource("charge_record")
                    .resourceId(recordId)
                    .build());
        }

        // Release charger
        chargerMapper.updateStatusConditionally(charger.getId(), "idle", "charging");

        return ChargeResponse.builder()
                .recordId(recordId)
                .endTime(LocalDateTime.now())
                .energyKwh(energyKwh)
                .fee(fee)
                .status("completed")
                .deductionStatus(deductionStatus.name().toLowerCase())
                .message(balanceSufficient ? "充电完成，已扣费" : "余额不足，已标记欠费")
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ChargeResponse forceStop(UUID adminId, UUID recordId, ForceStopRequest request, String clientIp) {
        // Sanitize reason
        String reason = sanitizeReason(request.getReason());

        Map<String, Object> recordData = chargeRecordMapper.findByIdWithLock(recordId);
        if (recordData == null) {
            throw BusinessException.notFound("ChargeRecord", recordId.toString());
        }

        ChargeRecord record = chargeRecordMapper.findById(recordId)
                .orElseThrow(() -> BusinessException.notFound("ChargeRecord", recordId.toString()));

        if (record.getStatus() != RecordStatus.PROCESSING) {
            throw BusinessException.conflict("记录状态不允许强制终止");
        }

        Charger charger = chargerMapper.findById(record.getChargerId())
                .orElseThrow(() -> BusinessException.notFound("Charger", record.getChargerId().toString()));

        User user = userMapper.findByIdWithLock(record.getUserId())
                .orElseThrow(() -> BusinessException.notFound("User", record.getUserId().toString()));

        // Calculate fee
        BigDecimal energyKwh = new BigDecimal("15.0"); // Mock energy
        BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

        DeductionStatus deductionStatus;

        if (user.getBalance().compareTo(fee) >= 0) {
            // Deduct
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "paid");
            int deductResult = userMapper.deductBalance(record.getUserId(), fee);
            if (deductResult > 0) {
                deductionStatus = DeductionStatus.PAID;
                paymentService.autoDeduct(record.getUserId(), fee, recordId);
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(adminId)
                        .actorType("admin")
                        .action("force_stop")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .payload("{\"reason\": \"" + reason + "\"}")
                        .clientIp(clientIp)
                        .build());
            } else {
                // Fall through to arrears
                chargeRecordMapper.updateDeductionStatus(recordId, "arrears");
                userMapper.freezeAccount(record.getUserId());
                deductionStatus = DeductionStatus.ARREARS;
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(adminId)
                        .actorType("admin")
                        .action("force_stop_arrears")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .payload("{\"reason\": \"" + reason + "\", \"deductionStatus\": \"arrears\"}")
                        .clientIp(clientIp)
                        .build());
            }
        } else {
            // Arrears
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "arrears");
            userMapper.freezeAccount(record.getUserId());
            deductionStatus = DeductionStatus.ARREARS;
            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(adminId)
                    .actorType("admin")
                    .action("force_stop_arrears")
                    .resource("charge_record")
                    .resourceId(recordId)
                    .payload("{\"reason\": \"" + reason + "\", \"deductionStatus\": \"arrears\"}")
                    .clientIp(clientIp)
                    .build());
        }

        // Release charger
        chargerMapper.updateStatusConditionally(charger.getId(), "idle", "charging");

        return ChargeResponse.builder()
                .recordId(recordId)
                .endTime(LocalDateTime.now())
                .energyKwh(energyKwh)
                .fee(fee)
                .status("completed")
                .deductionStatus(deductionStatus.name().toLowerCase())
                .message("已强制结束充电")
                .build();
    }

    @Override
    public List<Map<String, Object>> queryCharges(UUID userId, String userRole, Map<String, String> params) {
        List<ChargeRecord> records;
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);

        String status = params.get("status");
        if (isAdmin) {
            if (status != null) {
                records = chargeRecordMapper.findByUserIdAndStatus(null, status);
                // fall back to all
                records = chargeRecordMapper.findAll();
            } else {
                records = chargeRecordMapper.findAll();
            }
        } else {
            if (status != null) {
                records = chargeRecordMapper.findByUserIdAndStatus(userId, status);
            } else {
                records = chargeRecordMapper.findByUserId(userId);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ChargeRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("chargerId", r.getChargerId());
            m.put("startTime", r.getStartTime());
            m.put("endTime", r.getEndTime());
            m.put("energyKwh", r.getEnergyKwh());
            m.put("fee", r.getFee());
            m.put("status", r.getStatus().name().toLowerCase());
            m.put("deductionStatus", r.getDeductionStatus().name().toLowerCase());
            result.add(m);
        }
        return result;
    }

    private String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        // Strip HTML tags and truncate
        return reason.replaceAll("<[^>]*>", "").substring(0, Math.min(reason.length(), 200));
    }
}