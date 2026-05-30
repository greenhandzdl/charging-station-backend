package com.charging.controller;

import com.charging.infrastructure.dto.*;
import com.charging.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/charges")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ReportVO> getChargeReport(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(statisticsService.generateReport(params));
    }

    @GetMapping("/user-charges")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<UserChargeStatsVO>> getUserChargingStats(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(statisticsService.getUserChargingStats(params));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ReportVO> getRevenueStats(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(statisticsService.getRevenueStats(params));
    }

    @GetMapping("/utilization")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<UtilizationVO> getChargerUtilization() {
        return ResponseEntity.ok(statisticsService.getChargerUtilization());
    }

    @GetMapping("/fault-chargers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<FaultChargerVO>> getFaultChargers() {
        return ResponseEntity.ok(statisticsService.getFaultChargers());
    }

    @GetMapping("/stations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<StationAnalysisVO>> getStationAnalysis() {
        return ResponseEntity.ok(statisticsService.getStationAnalysis());
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportCsv(@RequestParam String type,
                                            @RequestParam Map<String, String> params) {
        byte[] csvData = statisticsService.exportCsv(type, params);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}