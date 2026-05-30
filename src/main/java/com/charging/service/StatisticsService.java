package com.charging.service;

import com.charging.infrastructure.dto.*;

import java.util.List;
import java.util.Map;

public interface StatisticsService {
    ReportVO generateReport(Map<String, String> params);
    List<UserChargeStatsVO> getUserChargingStats(Map<String, String> params);
    List<StationAnalysisVO> getStationAnalysis();
    UtilizationVO getChargerUtilization();
    ReportVO getRevenueStats(Map<String, String> params);
    List<FaultChargerVO> getFaultChargers();
    byte[] exportCsv(String type, Map<String, String> params);
}