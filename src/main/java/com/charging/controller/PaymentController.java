package com.charging.controller;

import com.charging.entity.Payment;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @PostMapping("/payments/recharge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RechargeResponse> recharge(@Valid @RequestBody RechargeRequest request,
                                                     @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        RechargeResponse response = paymentService.recharge(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/payments/callback")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> handleCallback(@RequestBody PaymentCallbackRequest callback,
                                                               @RequestHeader("X-Payment-Gateway-Key") String gatewayKey) {
        paymentService.processCallback(callback, gatewayKey);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/payments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Payment>> queryPayments(@RequestParam Map<String, String> params,
                                                       @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        LocalDateTime startTime = parseDateTime(params.get("startTime"));
        LocalDateTime endTime = parseDateTime(params.get("endTime"));
        BigDecimal minAmount = parseBigDecimal(params.get("minAmount"));
        BigDecimal maxAmount = parseBigDecimal(params.get("maxAmount"));
        String status = params.get("status");
        String keyword = params.get("keyword");
        List<Payment> payments = paymentService.queryPayments(userId, principal.getRole(),
                startTime, endTime, minAmount, maxAmount, status, keyword);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/payments/deductions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Payment>> queryDeductions(@RequestParam Map<String, String> params,
                                                          @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        LocalDateTime startTime = parseDateTime(params.get("startTime"));
        LocalDateTime endTime = parseDateTime(params.get("endTime"));
        BigDecimal minAmount = parseBigDecimal(params.get("minAmount"));
        BigDecimal maxAmount = parseBigDecimal(params.get("maxAmount"));
        String status = params.get("status");
        String keyword = params.get("keyword");
        List<Payment> deductions = paymentService.queryDeductions(userId, principal.getRole(),
                startTime, endTime, minAmount, maxAmount, status, keyword);
        return ResponseEntity.ok(deductions);
    }

    @GetMapping("/payments/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Payment>> listPendingPayments() {
        return ResponseEntity.ok(paymentService.listPendingPayments());
    }

    @PutMapping("/payments/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> approvePayment(@PathVariable UUID id,
                                                               @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID adminId = UUID.fromString(principal.getUserId());
        paymentService.approvePayment(id, adminId);
        return ResponseEntity.ok(Map.of("status", "approved"));
    }

    @PutMapping("/payments/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> rejectPayment(@PathVariable UUID id,
                                                              @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID adminId = UUID.fromString(principal.getUserId());
        paymentService.rejectPayment(id, adminId, "管理员拒绝");
        return ResponseEntity.ok(Map.of("status", "rejected"));
    }

    @PostMapping("/payments/pay-arrears")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> payArrears(@RequestBody Map<String, Object> request,
                                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        UUID recordId = UUID.fromString(request.get("recordId").toString());
        String method = request.get("method").toString();
        paymentService.payArrears(userId, recordId, method);
        return ResponseEntity.ok(Map.of("message", "支付成功"));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }
}