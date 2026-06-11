package com.charging.controller;

import com.charging.infrastructure.dto.*;
import com.charging.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;

    private StatisticsController statisticsController;

    @BeforeEach
    void setUp() {
        statisticsController = new StatisticsController(statisticsService);
    }

    // ==================== getChargeReport ====================

    @Test
    void getChargeReport_shouldReturn200WithReportData() {
        Map<String, String> params = Map.of("startDate", "2026-01-01", "endDate", "2026-06-01");
        ReportVO expectedReport = ReportVO.builder()
                .totalCharges(150L)
                .totalEnergy(new BigDecimal("1200.50"))
                .totalRevenue(new BigDecimal("3600.00"))
                .avgDailyRevenue(new BigDecimal("24.00"))
                .periodDays(150L)
                .details(Map.of("peakHours", "14:00-16:00"))
                .build();

        when(statisticsService.generateReport(params)).thenReturn(expectedReport);

        ResponseEntity<ReportVO> response = statisticsController.getChargeReport(params);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(150L, response.getBody().getTotalCharges());
        assertEquals(new BigDecimal("1200.50"), response.getBody().getTotalEnergy());
        assertEquals(new BigDecimal("3600.00"), response.getBody().getTotalRevenue());
        verify(statisticsService).generateReport(params);
    }

    // ==================== getUserChargingStats ====================

    @Test
    void getUserChargingStats_shouldReturn200WithStats() {
        Map<String, String> params = Map.of("sortBy", "totalFee", "order", "desc");
        UserChargeStatsVO stat1 = UserChargeStatsVO.builder()
                .userId(UUID.randomUUID())
                .userName("张三")
                .phone("13800138001")
                .chargeCount(20L)
                .totalEnergy(new BigDecimal("300.00"))
                .totalFee(new BigDecimal("900.00"))
                .build();
        UserChargeStatsVO stat2 = UserChargeStatsVO.builder()
                .userId(UUID.randomUUID())
                .userName("李四")
                .phone("13800138002")
                .chargeCount(15L)
                .totalEnergy(new BigDecimal("200.00"))
                .totalFee(new BigDecimal("600.00"))
                .build();
        List<UserChargeStatsVO> expectedStats = List.of(stat1, stat2);

        when(statisticsService.getUserChargingStats(params)).thenReturn(expectedStats);

        ResponseEntity<List<UserChargeStatsVO>> response = statisticsController.getUserChargingStats(params);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("张三", response.getBody().get(0).getUserName());
        assertEquals("李四", response.getBody().get(1).getUserName());
        verify(statisticsService).getUserChargingStats(params);
    }

    // ==================== getStationAnalysis ====================

    @Test
    void getStationAnalysis_shouldReturn200WithAnalysis() {
        StationAnalysisVO analysis1 = StationAnalysisVO.builder()
                .stationId("S001")
                .stationName("市中心站")
                .totalChargers(10L)
                .idleChargers(3L)
                .chargingChargers(5L)
                .faultChargers(2L)
                .totalCharges(500L)
                .totalEnergy(new BigDecimal("5000.00"))
                .totalRevenue(new BigDecimal("15000.00"))
                .utilizationRate(65.5)
                .build();
        StationAnalysisVO analysis2 = StationAnalysisVO.builder()
                .stationId("S002")
                .stationName("城北站")
                .totalChargers(8L)
                .idleChargers(4L)
                .chargingChargers(3L)
                .faultChargers(1L)
                .totalCharges(300L)
                .totalEnergy(new BigDecimal("3000.00"))
                .totalRevenue(new BigDecimal("9000.00"))
                .utilizationRate(45.0)
                .build();

        when(statisticsService.getStationAnalysis()).thenReturn(List.of(analysis1, analysis2));

        ResponseEntity<List<StationAnalysisVO>> response = statisticsController.getStationAnalysis();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("市中心站", response.getBody().get(0).getStationName());
        assertEquals("城北站", response.getBody().get(1).getStationName());
        verify(statisticsService).getStationAnalysis();
    }

    // ==================== getChargerUtilization ====================

    @Test
    void getChargerUtilization_shouldReturn200WithUtilizationData() {
        UtilizationVO expectedUtilization = UtilizationVO.builder()
                .idleCount(15L)
                .chargingCount(10L)
                .faultCount(5L)
                .idlePercent(50.0)
                .chargingPercent(33.33)
                .faultPercent(16.67)
                .build();

        when(statisticsService.getChargerUtilization()).thenReturn(expectedUtilization);

        ResponseEntity<UtilizationVO> response = statisticsController.getChargerUtilization();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(15L, response.getBody().getIdleCount());
        assertEquals(10L, response.getBody().getChargingCount());
        assertEquals(5L, response.getBody().getFaultCount());
        verify(statisticsService).getChargerUtilization();
    }

    // ==================== getFaultChargers ====================

    @Test
    void getFaultChargers_shouldReturn200WithFaultList() {
        FaultChargerVO fault1 = FaultChargerVO.builder()
                .chargerId(UUID.randomUUID())
                .chargerCode("F001")
                .stationId(UUID.randomUUID())
                .stationName("市中心站")
                .chargerType("FAST")
                .lastFaultTime(LocalDateTime.now().minusHours(2))
                .status("FAULT")
                .build();
        FaultChargerVO fault2 = FaultChargerVO.builder()
                .chargerId(UUID.randomUUID())
                .chargerCode("F002")
                .stationId(UUID.randomUUID())
                .stationName("城北站")
                .chargerType("SLOW")
                .lastFaultTime(LocalDateTime.now().minusDays(1))
                .status("FAULT")
                .build();

        when(statisticsService.getFaultChargers()).thenReturn(List.of(fault1, fault2));

        ResponseEntity<List<FaultChargerVO>> response = statisticsController.getFaultChargers();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals("F001", response.getBody().get(0).getChargerCode());
        assertEquals("F002", response.getBody().get(1).getChargerCode());
        verify(statisticsService).getFaultChargers();
    }

    @Test
    void getFaultChargers_whenEmpty_shouldReturnEmptyList() {
        when(statisticsService.getFaultChargers()).thenReturn(List.of());

        ResponseEntity<List<FaultChargerVO>> response = statisticsController.getFaultChargers();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
        verify(statisticsService).getFaultChargers();
    }

    // ==================== getRevenueStats ====================

    @Test
    void getRevenueStats_shouldReturn200() {
        Map<String, String> params = Map.of("year", "2026");
        ReportVO expectedRevenue = ReportVO.builder()
                .totalCharges(500L)
                .totalEnergy(new BigDecimal("5000.00"))
                .totalRevenue(new BigDecimal("15000.00"))
                .avgDailyRevenue(new BigDecimal("41.67"))
                .periodDays(360L)
                .details(Map.of("monthly", Map.of("January", 1200.00)))
                .build();

        when(statisticsService.getRevenueStats(params)).thenReturn(expectedRevenue);

        ResponseEntity<ReportVO> response = statisticsController.getRevenueStats(params);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("15000.00"), response.getBody().getTotalRevenue());
        verify(statisticsService).getRevenueStats(params);
    }

    // ==================== exportCsv ====================

    @Test
    void exportCsv_withTypeCharges_shouldReturn200WithCsvContent() {
        Map<String, String> params = Map.of("startDate", "2026-01-01");
        String csvContent = "ID,UserID,ChargerID,StartTime,EndTime,Energy,Fee\n1,u1,c1,2026-01-01,2026-01-01,10,30";
        byte[] expectedCsv = csvContent.getBytes(StandardCharsets.UTF_8);

        when(statisticsService.exportCsv("charges", params)).thenReturn(expectedCsv);

        ResponseEntity<byte[]> response = statisticsController.exportCsv("charges", params);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("attachment; filename=charges.csv",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(MediaType.parseMediaType("text/csv; charset=UTF-8"),
                response.getHeaders().getContentType());
        assertArrayEquals(expectedCsv, response.getBody());
        verify(statisticsService).exportCsv("charges", params);
    }

    @Test
    void exportCsv_withTypeUsers_shouldReturn200() {
        Map<String, String> params = Map.of();
        String csvContent = "ID,Name,Phone,TotalCharges,TotalEnergy,TotalFee\nu1,张三,13800138001,20,300,900";
        byte[] expectedCsv = csvContent.getBytes(StandardCharsets.UTF_8);

        when(statisticsService.exportCsv("users", params)).thenReturn(expectedCsv);

        ResponseEntity<byte[]> response = statisticsController.exportCsv("users", params);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=users.csv",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(expectedCsv, response.getBody());
        verify(statisticsService).exportCsv("users", params);
    }
}
