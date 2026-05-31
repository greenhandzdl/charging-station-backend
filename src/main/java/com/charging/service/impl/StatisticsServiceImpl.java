package com.charging.service.impl;

import com.charging.entity.Charger;
import com.charging.entity.Station;
import com.charging.entity.User;
import com.charging.infrastructure.dto.*;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.PaymentMapper;
import com.charging.mapper.RepairMapper;
import com.charging.mapper.StationMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final ChargeRecordMapper chargeRecordMapper;
    private final PaymentMapper paymentMapper;
    private final ChargerMapper chargerMapper;
    private final StationMapper stationMapper;
    private final UserMapper userMapper;
    private final RepairMapper repairMapper;

    @Override
    public ReportVO generateReport(Map<String, String> params) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        int totalCharges = chargeRecordMapper.countByDateRange(start, end);
        BigDecimal totalEnergy = chargeRecordMapper.sumEnergyByDateRange(start, end);

        return ReportVO.builder()
                .totalCharges(totalCharges)
                .totalEnergy(totalEnergy)
                .periodDays(30)
                .build();
    }

    @Override
    public List<UserChargeStatsVO> getUserChargingStats(Map<String, String> params) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Map<String, Object>> rawStats = chargeRecordMapper.getUserChargeStats(start, end);

        List<UserChargeStatsVO> result = new ArrayList<>();
        for (Map<String, Object> row : rawStats) {
            UUID userId = (UUID) row.get("user_id");
            UserChargeStatsVO vo = UserChargeStatsVO.builder()
                    .userId(userId)
                    .chargeCount(((Number) row.get("count")).longValue())
                    .totalEnergy((BigDecimal) row.get("total_energy"))
                    .totalFee((BigDecimal) row.get("total_fee"))
                    .build();

            // Enrich with user display info
            userMapper.findById(userId).ifPresent(user -> {
                vo.setUserName(user.getName());
                vo.setPhone(user.getPhone());
            });

            result.add(vo);
        }
        return result;
    }

    @Override
    public List<StationAnalysisVO> getStationAnalysis() {
        List<Station> stations = stationMapper.findAll();
        List<StationAnalysisVO> result = new ArrayList<>();

        for (Station station : stations) {
            List<Charger> chargers = chargerMapper.findByStationId(station.getId());

            long idleCount = chargers.stream()
                    .filter(c -> c.getStatus().name().equalsIgnoreCase("idle")).count();
            long chargingCount = chargers.stream()
                    .filter(c -> c.getStatus().name().equalsIgnoreCase("charging")).count();
            long faultCount = chargers.stream()
                    .filter(c -> c.getStatus().name().equalsIgnoreCase("fault")).count();

            double utilizationRate = chargers.isEmpty() ? 0.0 :
                    (double) (chargingCount + faultCount) / chargers.size() * 100;

            // Query charge stats for this station
            Map<String, Object> stats = chargeRecordMapper.getStationChargeStats(station.getId());
            long totalCharges = stats != null ? ((Number) stats.getOrDefault("count", 0)).longValue() : 0;
            BigDecimal totalEnergy = stats != null ? (BigDecimal) stats.getOrDefault("total_energy", BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal totalRevenue = stats != null ? (BigDecimal) stats.getOrDefault("total_fee", BigDecimal.ZERO) : BigDecimal.ZERO;

            result.add(StationAnalysisVO.builder()
                    .stationId(station.getId().toString())
                    .stationName(station.getName())
                    .totalChargers(chargers.size())
                    .idleChargers(idleCount)
                    .chargingChargers(chargingCount)
                    .faultChargers(faultCount)
                    .totalCharges(totalCharges)
                    .totalEnergy(totalEnergy)
                    .totalRevenue(totalRevenue)
                    .utilizationRate(BigDecimal.valueOf(utilizationRate)
                            .setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .build());
        }
        return result;
    }

    @Override
    public UtilizationVO getChargerUtilization() {
        List<Charger> allChargers = chargerMapper.findAll();

        long idleCount = allChargers.stream()
                .filter(c -> c.getStatus().name().equalsIgnoreCase("idle")).count();
        long chargingCount = allChargers.stream()
                .filter(c -> c.getStatus().name().equalsIgnoreCase("charging")).count();
        long faultCount = allChargers.stream()
                .filter(c -> c.getStatus().name().equalsIgnoreCase("fault")).count();

        long total = allChargers.isEmpty() ? 1 : allChargers.size();

        return UtilizationVO.builder()
                .idleCount(idleCount)
                .chargingCount(chargingCount)
                .faultCount(faultCount)
                .idlePercent(BigDecimal.valueOf(idleCount * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue())
                .chargingPercent(BigDecimal.valueOf(chargingCount * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue())
                .faultPercent(BigDecimal.valueOf(faultCount * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue())
                .build();
    }

    @Override
    public ReportVO getRevenueStats(Map<String, String> params) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        BigDecimal totalRevenue = paymentMapper.sumSuccessAmountByDateRange(start, end);
        long days = 30;
        BigDecimal avgDaily = totalRevenue.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        return ReportVO.builder()
                .totalRevenue(totalRevenue)
                .avgDailyRevenue(avgDaily)
                .periodDays(days)
                .build();
    }

    @Override
    public List<FaultChargerVO> getFaultChargers() {
        List<Charger> allChargers = chargerMapper.findAll();
        List<FaultChargerVO> result = new ArrayList<>();

        for (Charger charger : allChargers) {
            if (charger.getStatus().name().equalsIgnoreCase("fault")) {
                Station station = stationMapper.findById(charger.getStationId()).orElse(null);
                LocalDateTime lastFaultTime = repairMapper.findLatestFaultTime(charger.getId());
                result.add(FaultChargerVO.builder()
                        .chargerId(charger.getId())
                        .chargerCode(charger.getChargerCode())
                        .stationId(charger.getStationId())
                        .stationName(station != null ? station.getName() : "Unknown")
                        .chargerType(charger.getType().name().toLowerCase())
                        .lastFaultTime(lastFaultTime)
                        .status("FAULT")
                        .build());
            }
        }
        return result;
    }

    @Override
    public byte[] exportCsv(String type, Map<String, String> params) {
        StringBuilder csv = new StringBuilder();

        switch (type) {
            case "charges" -> {
                csv.append("ID,UserID,ChargerID,StartTime,EndTime,EnergyKwh,Fee,Status\n");
                List<com.charging.entity.ChargeRecord> records = chargeRecordMapper.findAll();
                for (com.charging.entity.ChargeRecord r : records) {
                    csv.append(r.getId()).append(",")
                            .append(r.getUserId()).append(",")
                            .append(r.getChargerId()).append(",")
                            .append(r.getStartTime()).append(",")
                            .append(r.getEndTime()).append(",")
                            .append(r.getEnergyKwh()).append(",")
                            .append(r.getFee()).append(",")
                            .append(r.getStatus()).append("\n");
                }
            }
            case "payments" -> {
                csv.append("ID,UserID,Amount,Method,Status,GatewayTxID,CreatedAt\n");
                List<com.charging.entity.Payment> payments = paymentMapper.findAll();
                for (com.charging.entity.Payment p : payments) {
                    csv.append(p.getId()).append(",")
                            .append(p.getUserId()).append(",")
                            .append(p.getAmount()).append(",")
                            .append(p.getMethod()).append(",")
                            .append(p.getStatus()).append(",")
                            .append(p.getGatewayTxId()).append(",")
                            .append(p.getCreatedAt()).append("\n");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported export type: " + type);
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}