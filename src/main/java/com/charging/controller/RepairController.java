package com.charging.controller;

import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.RepairService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
public class RepairController {

    private final RepairService repairService;

    @PostMapping("/repairs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RepairResponse> submitRepair(@Valid @RequestBody SubmitRepairRequest request,
                                                       @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        RepairResponse response = repairService.submit(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/repairs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RepairResponse>> listRepairs(
            @RequestParam Map<String, String> params,
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        List<RepairResponse> repairs = repairService.listRepairs(userId, principal.getRole(), params);
        return ResponseEntity.ok(repairs);
    }

    @PutMapping("/repairs/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> assignRepair(@PathVariable UUID id,
                                                            @Valid @RequestBody AssignRepairRequest request,
                                                            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID adminId = UUID.fromString(principal.getUserId());
        repairService.assign(id, request, adminId);
        return ResponseEntity.ok(Map.of("status", "in_progress"));
    }

    @PutMapping("/repairs/{id}/claim")
    @PreAuthorize("hasAnyRole('MAINTAINER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> claimRepair(@PathVariable UUID id,
                                                            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        repairService.claim(id, userId);
        return ResponseEntity.ok(Map.of("status", "in_progress"));
    }

    @PutMapping("/repairs/{id}/resolve")
    @PreAuthorize("hasAnyRole('MAINTAINER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> resolveRepair(@PathVariable UUID id,
                                                             @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        repairService.resolve(id, userId, principal.getRole());
        return ResponseEntity.ok(Map.of("status", "resolved"));
    }

    @PutMapping("/repairs/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> closeRepair(@PathVariable UUID id,
                                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID adminId = UUID.fromString(principal.getUserId());
        repairService.close(id, adminId);
        return ResponseEntity.ok(Map.of("status", "closed"));
    }

    @PutMapping("/repairs/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> rejectRepair(@PathVariable UUID id,
                                                            @Valid @RequestBody RejectRepairRequest request,
                                                            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID adminId = UUID.fromString(principal.getUserId());
        repairService.reject(id, request, adminId);
        return ResponseEntity.ok(Map.of("status", "in_progress"));
    }
}