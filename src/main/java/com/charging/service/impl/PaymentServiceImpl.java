package com.charging.service.impl;

import com.charging.channel.PaymentChannel;
import com.charging.entity.AuditLog;
import com.charging.entity.ChargeRecord;
import com.charging.entity.Payment;
import com.charging.entity.User;
import com.charging.exception.BusinessException;
import com.charging.factory.PaymentFactory;
import com.charging.infrastructure.dto.PaymentCallbackRequest;
import com.charging.infrastructure.dto.RechargeRequest;
import com.charging.infrastructure.dto.RechargeResponse;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.PaymentMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final ChargeRecordMapper chargeRecordMapper;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final PaymentFactory paymentFactory;

    @Value("${payment.hmac-secret:default-hmac-secret}")
    private String hmacSecret;

    @Override
    @Transactional
    public RechargeResponse recharge(UUID userId, RechargeRequest request) {
        // Idempotency check
        Optional<Payment> existing = paymentMapper.findByGatewayTxId(request.getIdempotencyKey());
        if (existing.isPresent()) {
            return RechargeResponse.builder()
                    .paymentId(existing.get().getId().toString())
                    .status("existing")
                    .build();
        }

        // Create payment record
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chargeRecordId(null)
                .method(request.getMethod())
                .amount(request.getAmount())
                .status(com.charging.enums.PaymentStatus.PENDING)
                .gatewayTxId(request.getIdempotencyKey())
                .createdAt(LocalDateTime.now())
                .build();

        paymentMapper.insert(payment);

        return RechargeResponse.builder()
                .paymentId(payment.getId().toString())
                .redirectUrl("/mock-gateway/" + payment.getId())
                .status("PENDING")
                .build();
    }

    @Override
    @Transactional
    public void processCallback(PaymentCallbackRequest callback, String gatewayKey) {
        // Verify gateway key (first line of defense)
        if (!hmacSecret.equals(gatewayKey)) {
            log.warn("Gateway key mismatch");
            return; // Don't throw, just ignore
        }

        UUID paymentId = UUID.fromString(callback.getPaymentId());
        Payment payment = paymentMapper.findById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("Payment", paymentId.toString()));

        // Idempotency check
        if (payment.getStatus() == com.charging.enums.PaymentStatus.SUCCESS) {
            log.info("Payment already processed: {}", paymentId);
            return;
        }

        // Verify signature via factory
        PaymentChannel channel = paymentFactory.createChannel(payment.getMethod());
        boolean signatureValid = channel.verifySignature(
                callback.getPaymentId() + callback.getAmount() + callback.getStatus(),
                callback.getSignature());

        if (!signatureValid) {
            // Log signature failure
            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(null)
                    .actorType("system")
                    .action("CALLBACK_SIGNATURE_FAILED")
                    .resource("payment")
                    .resourceId(paymentId)
                    .payload("{\"paymentId\": \"" + paymentId + "\"}")
                    .build());
            paymentMapper.markFailed(paymentId);
            return;
        }

        if (!"success".equals(callback.getStatus())) {
            paymentMapper.markFailed(paymentId);
            return;
        }

        // Mark payment as success
        paymentMapper.markSuccess(paymentId, callback.getTxId());

        // Add balance to user
        userMapper.addBalance(payment.getUserId(), payment.getAmount());

        // Auto-deduct arrears
        autoDeductArrears(payment.getUserId());

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(payment.getUserId())
                .actorType("user")
                .action("RECHARGE")
                .resource("payment")
                .resourceId(paymentId)
                .build());
    }

    @Override
    public List<Payment> queryPayments(UUID userId, String userRole) {
        boolean isAdmin = "ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole);
        if (isAdmin) {
            return paymentMapper.findAll();
        }
        return paymentMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public void autoDeduct(UUID userId, BigDecimal amount, UUID chargeRecordId) {
        // Create auto-deduct payment record
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chargeRecordId(chargeRecordId)
                .method("auto_deduct")
                .amount(amount)
                .status(com.charging.enums.PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        paymentMapper.insert(payment);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("system")
                .action("DEDUCT")
                .resource("charge_record")
                .resourceId(chargeRecordId)
                .build());
    }

    /**
     * Auto-deduct arrears when user recharges.
     * Processes arrears records in order, deducting from new balance.
     */
    @Override
    @Transactional
    public void payArrears(UUID userId, UUID recordId, String method) {
        // Find charge record
        ChargeRecord record = chargeRecordMapper.findById(recordId)
                .orElseThrow(() -> BusinessException.notFound("ChargeRecord", recordId.toString()));

        // Verify it belongs to this user
        if (!record.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作该记录");
        }

        // Verify it's in arrears
        if (record.getDeductionStatus() != com.charging.enums.DeductionStatus.ARREARS) {
            throw BusinessException.badRequest("该记录不是欠费状态");
        }

        BigDecimal fee = record.getFee();
        if (fee == null) {
            throw BusinessException.badRequest("费用为空");
        }

        // Deduct from user balance
        User user = userMapper.findByIdWithLock(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));
        if (user.getBalance().compareTo(fee) < 0) {
            throw BusinessException.insufficientBalance(user.getBalance(), fee);
        }

        int deductResult = userMapper.deductBalance(userId, fee);
        if (deductResult == 0) {
            throw BusinessException.conflict("扣款失败，请重试");
        }

        // Update charge record deduction status
        chargeRecordMapper.updateDeductionStatus(recordId, "PAID");

        // Create payment record
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chargeRecordId(recordId)
                .method(method)
                .amount(fee)
                .status(com.charging.enums.PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
        paymentMapper.insert(payment);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("PAY_ARREARS")
                .resource("charge_record")
                .resourceId(recordId)
                .payload("{\"method\": \"" + method + "\", \"amount\": " + fee + "}")
                .build());

        // Check if all arrears cleared - unfreeze
        List<ChargeRecord> remainingArrears = chargeRecordMapper.findArrearsByUserId(userId);
        if (remainingArrears.isEmpty()) {
            userMapper.unfreezeAccount(userId);
        }
    }

    @Transactional
    protected void autoDeductArrears(UUID userId) {
        List<ChargeRecord> arrearsRecords = chargeRecordMapper.findArrearsByUserIdWithLock(userId);

        for (ChargeRecord arrears : arrearsRecords) {
            User user = userMapper.findByIdWithLock(userId)
                    .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));

            BigDecimal fee = arrears.getFee();
            if (fee != null && user.getBalance().compareTo(fee) >= 0) {
                // Deduct
                int result = userMapper.deductBalance(userId, fee);
                if (result > 0) {
                    chargeRecordMapper.updateDeductionStatus(arrears.getId(), "PAID");

                    Payment payment = Payment.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .chargeRecordId(arrears.getId())
                            .method("auto_deduct")
                            .amount(fee)
                            .status(com.charging.enums.PaymentStatus.SUCCESS)
                            .createdAt(LocalDateTime.now())
                            .build();
                    paymentMapper.insert(payment);

                    auditLogMapper.insert(AuditLog.builder()
                            .id(UUID.randomUUID())
                            .actorId(userId)
                            .actorType("system")
                            .action("ARREARS_AUTO_DEDUCT")
                            .resource("charge_record")
                            .resourceId(arrears.getId())
                            .build());
                }
            } else {
                break; // Insufficient balance for this arrears
            }
        }

        // Check if all arrears cleared - unfreeze
        List<ChargeRecord> remainingArrears = chargeRecordMapper.findArrearsByUserId(userId);
        if (remainingArrears.isEmpty()) {
            userMapper.unfreezeAccount(userId);
        }
    }
}