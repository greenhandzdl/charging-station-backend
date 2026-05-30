package com.charging.controller;

import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.ChargingService;
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
public class ChargingController {

    private final ChargingService chargingService;

    @PostMapping("/charges/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChargeResponse> startCharge(@Valid @RequestBody StartChargeRequest request,
                                                      @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        ChargeResponse response = chargingService.startCharge(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charges/stop")
    @PreAuthorize("@chargeGuard.canStop(authentication, #req.recordId)")
    public ResponseEntity<ChargeResponse> stopCharge(@Valid @RequestBody StopChargeRequest req,
                                                     @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        ChargeResponse response = chargingService.stopCharge(userId, principal.getRole(), req);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/charges/{id}/force-stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ChargeResponse> forceStop(@PathVariable UUID id,
                                                    @Valid @RequestBody ForceStopRequest request,
                                                    @AuthenticationPrincipal JwtUserPrincipal principal,
                                                    jakarta.servlet.http.HttpServletRequest httpRequest) {
        UUID adminId = UUID.fromString(principal.getUserId());
        String clientIp = getClientIp(httpRequest);
        ChargeResponse response = chargingService.forceStop(adminId, id, request, clientIp);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/charges")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> queryCharges(
            @RequestParam Map<String, String> params,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        List<Map<String, Object>> records = chargingService.queryCharges(userId, principal.getRole(), params);
        return ResponseEntity.ok(records);
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}