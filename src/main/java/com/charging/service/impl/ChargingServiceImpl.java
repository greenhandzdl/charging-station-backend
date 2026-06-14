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
import com.charging.infrastructure.connector.ChargerConnector;
import com.charging.service.BillingService;
import com.charging.service.ChargingService;
import com.charging.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ChargerConnector chargerConnector;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to convert to JSON", e);
            return "{}";
        }
    }

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${charging.min-balance:10.00}")
    private BigDecimal minBalance;

    @Value("${charging.fast-rate-kwh-per-hour:60}")
    private double fastRateKwhPerHour;

    @Value("${charging.slow-rate-kwh-per-hour:7}")
    private double slowRateKwhPerHour;

    // 临时存储充电桩的 sessionId（QR 会话绑定），key: chargerId, value: sessionId
    private final Map<String, String> chargerSessions = new ConcurrentHashMap<>();

    /**
     * 根据充电桩类型和充电时长计算估算用电量（kWh）。
     * 快充桩默认 60 kWh/h，慢充桩默认 7 kWh/h。
     */
    private BigDecimal estimateEnergyKwh(ChargerType type, LocalDateTime startTime) {
        if (startTime == null) {
            // startTime 为 null 时（早期流程缺陷或离线桩），按默认值估算
            // 快充 60kW/h × 0.5h = 30kWh, 慢充 7kW/h × 0.5h = 3.5kWh
            double defaultKwh = type == ChargerType.FAST ? 30.0 : 3.5;
            return BigDecimal.valueOf(defaultKwh).setScale(1, RoundingMode.HALF_UP);
        }
        Duration duration = Duration.between(startTime, LocalDateTime.now());
        double hours = duration.getSeconds() / 3600.0;
        if (hours <= 0) {
            hours = 0.5; // 至少按30分钟估算
        }
        double rate = type == ChargerType.FAST ? fastRateKwhPerHour : slowRateKwhPerHour;
        double energy = hours * rate;
        // 上限保护：快充最高 200 kWh，慢充最高 100 kWh
        double maxEnergy = type == ChargerType.FAST ? 200.0 : 100.0;
        if (energy > maxEnergy) {
            energy = maxEnergy;
        }
        return BigDecimal.valueOf(energy).setScale(1, RoundingMode.HALF_UP);
    }

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

        // Check charger is online
        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw BusinessException.chargerOffline();
        }

        // Check charger is occupied (plugged in)
        if (charger.getOccupiedBy() == null) {
            throw BusinessException.conflict("充电桩未插枪，请先插枪");
        }

        // Lock charger (optimistic write)
        int updated = chargerMapper.updateStatusConditionally(chargerId, "CHARGING", "IDLE");
        if (updated == 0) {
            throw BusinessException.conflict("充电桩已被占用");
        }

        // Create charge record
        ChargeRecord record = ChargeRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chargerId(chargerId)
                .status(RecordStatus.PROCESSING)
                .deductionStatus(DeductionStatus.PENDING)
                .build();

        chargeRecordMapper.insert(record);

        // Notify charger via connector
        chargerConnector.notifyStart(charger.getChargerCode(), record.getId());

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("START_CHARGE")
                .resource("charger")
                .resourceId(chargerId)
                .build());

        return ChargeResponse.builder()
                .recordId(record.getId())
                .startTime(record.getStartTime())
                .status("PROCESSING")
                .message("充电已启动")
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> plugIn(UUID chargerId, UUID deviceUserId) {
        // 验证充电桩存在
        Charger charger = chargerMapper.findById(chargerId)
                .orElseThrow(() -> BusinessException.notFound("Charger", chargerId.toString()));

        // 验证桩在线
        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw BusinessException.chargerOffline();
        }

        // 检查桩未被占用
        if (charger.getOccupiedBy() != null) {
            throw BusinessException.conflict("充电桩已被其他用户占用");
        }

        // 设置占用锁
        int updated = chargerMapper.occupy(chargerId, deviceUserId);
        if (updated == 0) {
            throw BusinessException.conflict("充电桩占用失败，请重试");
        }

        // 生成 sessionId（QR 绑定用）
        String sessionId = UUID.randomUUID().toString();
        chargerSessions.put(chargerId.toString(), sessionId);

        // 审计日志
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(deviceUserId)
                .actorType("device")
                .action("PLUG_IN")
                .resource("charger")
                .resourceId(chargerId)
                .build());

        log.info("Charger {} plugged in, sessionId={}", chargerId, sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chargerId", chargerId.toString());
        result.put("sessionId", sessionId);
        result.put("message", "插枪成功");
        return result;
    }

    /**
     * 定时检查进行中的充电记录，当用户余额低于阈值时自动停止充电。
     * 由 ChargingScheduler 定期调用（每 30 秒）。
     */
    @Override
    @Transactional
    public int autoStopOnInsufficientBalance() {
        List<ChargeRecord> processingRecords = chargeRecordMapper.findProcessingRecordsWithStartTime();
        int stopped = 0;
        for (ChargeRecord record : processingRecords) {
            try {
                User user = userMapper.findById(record.getUserId()).orElse(null);
                if (user == null) continue;

                Charger charger = chargerMapper.findById(record.getChargerId()).orElse(null);
                if (charger == null) continue;

                // If balance < min_balance, auto-stop
                if (user.getBalance().compareTo(minBalance) < 0) {
                    BigDecimal energyKwh = estimateEnergyKwh(charger.getType(), record.getStartTime());
                    BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

                    chargeRecordMapper.completeRecord(record.getId(), energyKwh, fee, "ARREARS");
                    userMapper.freezeAccount(record.getUserId());
                    chargerMapper.updateStatusConditionally(charger.getId(), "IDLE", "CHARGING");
                    chargerConnector.notifyStop(charger.getChargerCode(), record.getId());

                    auditLogMapper.insert(AuditLog.builder()
                            .id(UUID.randomUUID())
                            .actorId(record.getUserId())
                            .actorType("system")
                            .action("FORCE_STOP_ARREARS")
                            .resource("charge_record")
                            .resourceId(record.getId())
                            .payload("{\"reason\": \"余额不足，自动停止充电\", \"balance\": \""
                                    + user.getBalance() + "\"}")
                            .build());
                    stopped++;
                    log.info("Auto-stopped charge {} due to insufficient balance ({})",
                            record.getId(), user.getBalance());
                }
            } catch (Exception e) {
                log.error("Error auto-stopping charge record {}", record.getId(), e);
            }
        }
        return stopped;
    }

    @Override
    @Transactional
    public Map<String, Object> unplug(UUID chargerId, UUID deviceUserId) {
        Charger charger = chargerMapper.findById(chargerId)
                .orElseThrow(() -> BusinessException.notFound("Charger", chargerId.toString()));

        // 无论是否有人占用，都要清理会话
        chargerSessions.remove(chargerId.toString());

        // 查找该桩上进行中的充电记录，自动结束
        List<ChargeRecord> processingRecords = chargeRecordMapper.findProcessingByChargerId(chargerId);
        int stopped = 0;
        for (ChargeRecord record : processingRecords) {
            try {
                BigDecimal energyKwh = estimateEnergyKwh(charger.getType(), record.getStartTime());
                BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

                User user = userMapper.findByIdWithLock(record.getUserId())
                        .orElse(null);
                boolean paid = false;
                if (user != null && user.getBalance().compareTo(fee) >= 0) {
                    int deductResult = userMapper.deductBalance(record.getUserId(), fee);
                    if (deductResult > 0) {
                        chargeRecordMapper.completeRecord(record.getId(), energyKwh, fee, "PAID");
                        paymentService.autoDeduct(record.getUserId(), fee, record.getId());
                        paid = true;
                    }
                }
                if (!paid) {
                    chargeRecordMapper.completeRecord(record.getId(), energyKwh, fee, "ARREARS");
                    if (user != null) {
                        userMapper.freezeAccount(record.getUserId());
                    }
                }

                // Notify charger via connector
                chargerConnector.notifyStop(charger.getChargerCode(), record.getId());

                // Redis通知Flutter客户端（轮询 GET /charges/active 可感知）
                if (redisTemplate != null) {
                    String notifyKey = "offline:notify:" + record.getUserId();
                    Map<String, Object> offlineData = new HashMap<>();
                    offlineData.put("recordId", record.getId().toString());
                    offlineData.put("chargerCode", charger.getChargerCode());
                    offlineData.put("energyKwh", energyKwh);
                    offlineData.put("fee", fee);
                    redisTemplate.opsForValue().set(notifyKey, offlineData);
                    redisTemplate.expire(notifyKey, java.time.Duration.ofMinutes(5));
                }
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(record.getUserId())
                        .actorType("system")
                        .action("STOP_CHARGE")
                        .resource("charge_record")
                        .resourceId(record.getId())
                        .payload(toJson(Map.of("reason", "unplug")))
                        .build());

                log.info("Charge {} auto-stopped due to unplug", record.getId());
                stopped++;
            } catch (Exception e) {
                log.error("Error auto-stopping charge {} on unplug", record.getId(), e);
            }
        }

        // 释放占用锁并强制设为空闲
        chargerMapper.releaseForce(chargerId);
        chargerMapper.updateStatus(chargerId, ChargerStatus.IDLE);

        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(deviceUserId)
                .actorType("device")
                .action("UNPLUG")
                .resource("charger")
                .resourceId(chargerId)
                .build());

        log.info("Charger {} unplugged by deviceUser {}", chargerId, deviceUserId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chargerId", chargerId.toString());
        result.put("stoppedRecords", stopped);
        result.put("message", "拔枪成功");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> selectCharger(UUID chargerId, UUID userId, String sessionId) {
        Charger charger = chargerMapper.findById(chargerId)
                .orElseThrow(() -> BusinessException.notFound("Charger", chargerId.toString()));

        if (!"ONLINE".equals(charger.getOnlineStatus())) {
            throw BusinessException.chargerOffline();
        }

        if (charger.getOccupiedBy() == null) {
            throw BusinessException.conflict("充电桩未插枪，请先插枪");
        }

        String storedSession = chargerSessions.get(chargerId.toString());
        if (storedSession == null || !storedSession.equals(sessionId)) {
            throw BusinessException.conflict("会话已过期或无效，请重新扫码");
        }

        log.info("User {} selected charger {} (sessionId={})", userId, chargerId, sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chargerId", chargerId.toString());
        result.put("chargerCode", charger.getChargerCode());
        result.put("stationId", charger.getStationId());
        result.put("type", charger.getType());
        result.put("deviceType", charger.getDeviceType());
        result.put("ratedPowerKw", charger.getRatedPowerKw());
        result.put("sessionId", sessionId);
        result.put("message", "选择充电桩成功");
        return result;
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

        // Calculate fee based on actual charging duration
        BigDecimal energyKwh = estimateEnergyKwh(charger.getType(), record.getStartTime());
        BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

        boolean balanceSufficient = false;
        DeductionStatus deductionStatus;

        if (user.getBalance().compareTo(fee) >= 0) {
            // Sufficient balance - deduct
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "PAID");

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
                        .action("STOP_CHARGE_DEDUCTED")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .build());
            } else {
                // Concurrent deduction issue - treat as arrears
                chargeRecordMapper.updateDeductionStatus(recordId, "ARREARS");
                userMapper.freezeAccount(userId);
                deductionStatus = DeductionStatus.ARREARS;
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(userId)
                        .actorType("system")
                        .action("CHARGE_ARREARS")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .build());
            }
        } else {
            // Insufficient balance - mark arrears
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "ARREARS");
            userMapper.freezeAccount(userId);
            deductionStatus = DeductionStatus.ARREARS;

            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(userId)
                    .actorType("user")
                    .action("CHARGE_ARREARS")
                    .resource("charge_record")
                    .resourceId(recordId)
                    .build());
        }

        // Release charger
        chargerMapper.updateStatusConditionally(charger.getId(), "IDLE", "CHARGING");

        // Notify charger via connector
        chargerConnector.notifyStop(charger.getChargerCode(), recordId);

        return ChargeResponse.builder()
                .recordId(recordId)
                .endTime(LocalDateTime.now())
                .energyKwh(energyKwh)
                .fee(fee)
                .status("COMPLETED")
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

        // Calculate fee based on actual charging duration
        BigDecimal energyKwh = estimateEnergyKwh(charger.getType(), record.getStartTime());
        BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

        DeductionStatus deductionStatus;

        if (user.getBalance().compareTo(fee) >= 0) {
            // Deduct
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "PAID");
            int deductResult = userMapper.deductBalance(record.getUserId(), fee);
            if (deductResult > 0) {
                deductionStatus = DeductionStatus.PAID;
                paymentService.autoDeduct(record.getUserId(), fee, recordId);
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(adminId)
                        .actorType("admin")
                        .action("FORCE_STOP")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .payload("{\"reason\": \"" + reason + "\"}")
                        .clientIp(clientIp)
                        .build());
            } else {
                // Fall through to arrears
                chargeRecordMapper.updateDeductionStatus(recordId, "ARREARS");
                userMapper.freezeAccount(record.getUserId());
                deductionStatus = DeductionStatus.ARREARS;
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(adminId)
                        .actorType("admin")
                        .action("FORCE_STOP_ARREARS")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .payload("{\"reason\": \"" + reason + "\", \"deductionStatus\": \"arrears\"}")
                        .clientIp(clientIp)
                        .build());
            }
        } else {
            // Arrears
            chargeRecordMapper.completeRecord(recordId, energyKwh, fee, "ARREARS");
            userMapper.freezeAccount(record.getUserId());
            deductionStatus = DeductionStatus.ARREARS;
            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(adminId)
                    .actorType("admin")
                        .action("FORCE_STOP_ARREARS")
                        .resource("charge_record")
                        .resourceId(recordId)
                        .payload("{\"reason\": \"" + reason + "\", \"deductionStatus\": \"arrears\"}")
                        .clientIp(clientIp)
                        .build());
        }

        // Release charger
        chargerMapper.updateStatusConditionally(charger.getId(), "IDLE", "CHARGING");

        // Notify charger via connector
        chargerConnector.notifyStop(charger.getChargerCode(), recordId);

        return ChargeResponse.builder()
                .recordId(recordId)
                .endTime(LocalDateTime.now())
                .energyKwh(energyKwh)
                .fee(fee)
                .status("COMPLETED")
                .deductionStatus(deductionStatus.name().toLowerCase())
                .message("已强制结束充电")
                .build();
    }

    @Override
    public List<Map<String, Object>> queryCharges(UUID userId, String userRole, Map<String, String> params) {
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);

        String status = params.get("status");
        String recordId = params.get("recordId");
        List<Map<String, Object>> records;

        // If filtering by specific recordId (used by Mock client getChargeStatus)
        if (recordId != null && !recordId.isEmpty()) {
            try {
                UUID rid = UUID.fromString(recordId);
                Map<String, Object> single = chargeRecordMapper.findEnrichedById(rid);
                if (single != null) {
                    records = List.of(single);
                } else {
                    records = List.of();
                }
                return buildChargeResult(records);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }

        if (isAdmin) {
            if (status != null) {
                records = chargeRecordMapper.findEnrichedByStatus(status);
            } else {
                records = chargeRecordMapper.findEnrichedAll();
            }
        } else {
            if (status != null) {
                // Filter in memory since enriched query filters by status unconditionally
                records = chargeRecordMapper.findEnrichedByUserId(userId);
                records.removeIf(r -> !status.equalsIgnoreCase((String) r.get("status")));
            } else {
                records = chargeRecordMapper.findEnrichedByUserId(userId);
            }
        }

        return buildChargeResult(records);
    }

    @Override
    @Transactional
    public int forceStopByChargerId(UUID chargerId, String reason) {
        // 查找该充电桩上正在进行的充电记录
        List<ChargeRecord> processingRecords = chargeRecordMapper.findProcessingByChargerId(chargerId);
        int stopped = 0;
        for (ChargeRecord record : processingRecords) {
            try {
                Charger charger = chargerMapper.findById(chargerId)
                        .orElseThrow(() -> BusinessException.notFound("Charger", chargerId.toString()));

                // 按实际充电时长估算用电量和费用
                BigDecimal energyKwh = estimateEnergyKwh(charger.getType(), record.getStartTime());
                BigDecimal fee = billingService.calculateFee(charger.getType(), energyKwh);

                // 检查用户余额并决定结算状态
                User user = userMapper.findByIdWithLock(record.getUserId()).orElse(null);
                boolean paid = false;
                
                if (user != null && user.getBalance().compareTo(fee) >= 0) {
                    // 余额充足，尝试扣费
                    int deductResult = userMapper.deductBalance(record.getUserId(), fee);
                    if (deductResult > 0) {
                        // 扣费成功，标记为PAID
                        chargeRecordMapper.completeRecord(record.getId(), energyKwh, fee, "PAID");
                        
                        // 创建自动扣费记录
                        paymentService.autoDeduct(record.getUserId(), fee, record.getId());
                        
                        paid = true;
                        log.info("Force-stopped charge {} on offline charger {}, balance sufficient, deducted {}", 
                                record.getId(), charger.getChargerCode(), fee);
                    }
                }
                
                if (!paid) {
                    // 余额不足或扣费失败，标记为ARREARS
                    chargeRecordMapper.completeRecord(record.getId(), energyKwh, fee, "ARREARS");
                    if (user != null) {
                        userMapper.freezeAccount(record.getUserId());
                    }
                    log.warn("Force-stopped charge {} on offline charger {}, insufficient balance, marked as ARREARS", 
                            record.getId(), charger.getChargerCode());
                }

                int statusUpdated = chargerMapper.updateStatusConditionally(charger.getId(), "IDLE", "CHARGING");
                if (statusUpdated == 0) {
                    log.warn("forceStopByChargerId: charger {} status was not CHARGING (already IDLE/FAULT), updating unconditionally", charger.getId());
                    chargerMapper.updateStatus(charger.getId(), ChargerStatus.IDLE);
                }
                chargerConnector.notifyStop(charger.getChargerCode(), record.getId());

                // Redis通知Flutter客户端（轮询 GET /charges/active 可感知）
                if (redisTemplate != null) {
                    String notifyKey = "offline:notify:" + record.getUserId();
                    Map<String, Object> offlineData = new HashMap<>();
                    offlineData.put("recordId", record.getId().toString());
                    offlineData.put("chargerCode", charger.getChargerCode());
                    offlineData.put("energyKwh", energyKwh);
                    offlineData.put("fee", fee);
                    redisTemplate.opsForValue().set(notifyKey, offlineData);
                    redisTemplate.expire(notifyKey, java.time.Duration.ofMinutes(5));
                }

                // 记录审计日志
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(record.getUserId())
                        .actorType("system")
                        .action("FORCE_STOP_OFFLINE")
                        .resource("charge_record")
                        .resourceId(record.getId())
                        .payload("{\"reason\": \"" + sanitizeReason(reason) + "\"}")
                        .build());

                stopped++;
                log.warn("Force-stopped charge {} on offline charger {} (reason: {})",
                        record.getId(), charger.getChargerCode(), reason);
            } catch (Exception e) {
                log.error("Error force-stopping charge record {} on offline charger",
                        record.getId(), e);
            }
        }
        return stopped;
    }

    /**
     * Build enriched charge record response from DB query results.
     * Converts snake_case DB columns to camelCase JSON keys.
     */
    private List<Map<String, Object>> buildChargeResult(List<Map<String, Object>> records) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("userId", r.get("user_id"));
            m.put("chargerId", r.get("charger_id"));
            m.put("startTime", r.get("start_time"));
            m.put("endTime", r.get("end_time"));
            m.put("energyKwh", r.get("energy_kwh"));
            m.put("fee", r.get("fee"));
            m.put("status", r.get("status"));
            m.put("deductionStatus", r.get("deduction_status"));
            m.put("userName", r.get("user_name"));
            m.put("plateNumber", r.get("plate_number"));
            m.put("chargerCode", r.get("charger_code"));
            m.put("stationName", r.get("station_name"));
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getActiveChargesWithChargerInfo(UUID userId) {
        // 查询用户所有 PROCESSING 状态的充电记录，关联充电桩信息
        List<ChargeRecord> activeRecords = chargeRecordMapper.findByUserIdAndStatus(userId, "PROCESSING");
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChargeRecord record : activeRecords) {
            chargerMapper.findById(record.getChargerId()).ifPresent(charger -> {
                Map<String, Object> chargeInfo = new HashMap<>();
                chargeInfo.put("recordId", record.getId().toString());
                chargeInfo.put("chargerId", charger.getId().toString());
                chargeInfo.put("chargerCode", charger.getChargerCode());
                chargeInfo.put("type", charger.getType().name());
                chargeInfo.put("ratedPowerKw", charger.getRatedPowerKw() != null ? charger.getRatedPowerKw().doubleValue() :
                        (charger.getType() == com.charging.enums.ChargerType.FAST ? 60.0 : 7.0));
                chargeInfo.put("startTime", record.getStartTime() != null ? record.getStartTime().toString() : null);
                chargeInfo.put("status", record.getStatus().name());
                result.add(chargeInfo);
            });
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