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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

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
        List<Payment> payments = paymentService.queryPayments(userId, principal.getRole());
        return ResponseEntity.ok(payments);
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
}