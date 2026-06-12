package com.charging.controller;

import com.charging.entity.Charger;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ChargingController {

    private final ChargingService chargingService;
    private final ChargerMapper chargerMapper;

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


    @PostMapping("/chargers/heartbeat")
    public ResponseEntity<Map<String, Object>> receiveHeartbeat(@RequestBody Map<String, String> request) {
        String chargerCode = request.get("chargerCode");
        if (chargerCode == null || chargerCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "chargerCode is required"));
        }

        // Gracefully handle unknown chargers (heartbeat is fire-and-forget telemetry)
        Charger charger = chargerMapper.findByChargerCode(chargerCode).orElse(null);
        if (charger == null) {
            log.warn("Heartbeat received for unknown charger: {} (ignored)", chargerCode);
            return ResponseEntity.ok(Map.of("status", "IGNORED", "chargerCode", chargerCode, "reason", "unknown_charger"));
        }

        // Update heartbeat timestamp and online status
        chargerMapper.updateHeartbeat(charger.getId());

        log.debug("Heartbeat received from charger: {}", chargerCode);
        return ResponseEntity.ok(Map.of("status", "OK", "chargerCode", chargerCode));
    }

    @PostMapping("/chargers/{id}/plug-in")
    @PreAuthorize("hasAuthority('SCOPE_charger')")
    public ResponseEntity<Map<String, Object>> plugIn(@PathVariable UUID id,
                                                      @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID deviceUserId = UUID.fromString(principal.getUserId());
        Map<String, Object> result = chargingService.plugIn(id, deviceUserId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/chargers/{id}/unplug")
    @PreAuthorize("hasAuthority('SCOPE_charger')")
    public ResponseEntity<Map<String, Object>> unplug(@PathVariable UUID id) {
        Map<String, Object> result = chargingService.unplug(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/chargers/{id}/select")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> selectCharger(@PathVariable UUID id,
                                                             @RequestBody Map<String, String> body,
                                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        Map<String, Object> result = chargingService.selectCharger(id, userId, body.get("sessionId"));
        return ResponseEntity.ok(result);
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