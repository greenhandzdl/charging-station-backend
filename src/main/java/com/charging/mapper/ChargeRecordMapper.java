package com.charging.mapper;

import com.charging.entity.ChargeRecord;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ChargeRecordMapper {

    @Insert("INSERT INTO charge_records (id, user_id, charger_id, start_time, status, deduction_status, created_at) " +
            "VALUES (#{id}, #{userId}, #{chargerId}, now(), 'PROCESSING', 'PENDING', now())")
    int insert(ChargeRecord record);

    @Update("UPDATE charge_records SET end_time = now(), energy_kwh = #{energy}, fee = #{fee}, " +
            "status = 'COMPLETED', deduction_status = #{deductionStatus} WHERE id = #{id}")
    int completeRecord(@Param("id") UUID id,
                       @Param("energy") BigDecimal energy,
                       @Param("fee") BigDecimal fee,
                       @Param("deductionStatus") String deductionStatus);

    @Update("UPDATE charge_records SET deduction_status = #{deductionStatus} WHERE id = #{id}")
    int updateDeductionStatus(@Param("id") UUID id, @Param("deductionStatus") String deductionStatus);

    @Select("SELECT cr.*, c.status as charger_status, u.balance " +
            "FROM charge_records cr " +
            "JOIN chargers c ON cr.charger_id = c.id " +
            "JOIN users u ON cr.user_id = u.id " +
            "WHERE cr.id = #{id} FOR UPDATE")
    Map<String, Object> findByIdWithLock(UUID id);

    @Select("SELECT * FROM charge_records WHERE id = #{id}")
    Optional<ChargeRecord> findById(UUID id);

    @Select("SELECT * FROM charge_records WHERE user_id = #{userId} ORDER BY start_time DESC")
    List<ChargeRecord> findByUserId(@Param("userId") UUID userId);

    @Select("SELECT * FROM charge_records WHERE user_id = #{userId} AND status = #{status} ORDER BY start_time DESC")
    List<ChargeRecord> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Select("SELECT * FROM charge_records ORDER BY start_time DESC")
    List<ChargeRecord> findAll();

    @Select("SELECT * FROM charge_records WHERE status = 'PROCESSING'")
    List<ChargeRecord> findProcessingRecords();

    @Select("SELECT COUNT(*) FROM charge_records WHERE user_id = #{userId} AND status = 'PROCESSING'")
    int countProcessingByUserId(UUID userId);

    @Select("SELECT * FROM charge_records WHERE deduction_status = 'ARREARS' AND user_id = #{userId} FOR UPDATE")
    List<ChargeRecord> findArrearsByUserIdWithLock(UUID userId);

    @Select("SELECT * FROM charge_records WHERE deduction_status = 'ARREARS' AND user_id = #{userId}")
    List<ChargeRecord> findArrearsByUserId(UUID userId);

    // Statistics queries
    @Select("SELECT COUNT(*) FROM charge_records WHERE created_at >= #{start} AND created_at < #{end}")
    int countByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(energy_kwh), 0) FROM charge_records WHERE created_at >= #{start} AND created_at < #{end}")
    BigDecimal sumEnergyByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(SUM(fee), 0) FROM charge_records WHERE status = 'COMPLETED' AND created_at >= #{start} AND created_at < #{end}")
    BigDecimal sumFeeByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT user_id, COUNT(*) as count, COALESCE(SUM(energy_kwh), 0) as total_energy, " +
            "COALESCE(SUM(fee), 0) as total_fee " +
            "FROM charge_records " +
            "WHERE created_at >= #{start} AND created_at < #{end} " +
            "GROUP BY user_id " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getUserChargeStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT charger_id, COUNT(*) as count, COALESCE(SUM(energy_kwh), 0) as total_energy " +
            "FROM charge_records " +
            "WHERE created_at >= #{start} AND created_at < #{end} " +
            "GROUP BY charger_id")
    List<Map<String, Object>> getChargerUsageStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Enriched queries with JOINs for display data
    @Select("SELECT cr.id, cr.user_id, cr.charger_id, cr.start_time, cr.end_time, " +
            "cr.energy_kwh, cr.fee, cr.status, cr.deduction_status, " +
            "u.name as user_name, u.plate_number, " +
            "ch.charger_code, s.name as station_name " +
            "FROM charge_records cr " +
            "JOIN users u ON cr.user_id = u.id " +
            "JOIN chargers ch ON cr.charger_id = ch.id " +
            "JOIN stations s ON ch.station_id = s.id " +
            "WHERE cr.user_id = #{userId} ORDER BY cr.start_time DESC")
    List<Map<String, Object>> findEnrichedByUserId(@Param("userId") UUID userId);

    @Select("SELECT cr.id, cr.user_id, cr.charger_id, cr.start_time, cr.end_time, " +
            "cr.energy_kwh, cr.fee, cr.status, cr.deduction_status, " +
            "u.name as user_name, u.plate_number, " +
            "ch.charger_code, s.name as station_name " +
            "FROM charge_records cr " +
            "JOIN users u ON cr.user_id = u.id " +
            "JOIN chargers ch ON cr.charger_id = ch.id " +
            "JOIN stations s ON ch.station_id = s.id " +
            "ORDER BY cr.start_time DESC")
    List<Map<String, Object>> findEnrichedAll();

    @Select("SELECT cr.id, cr.user_id, cr.charger_id, cr.start_time, cr.end_time, " +
            "cr.energy_kwh, cr.fee, cr.status, cr.deduction_status, " +
            "u.name as user_name, u.plate_number, " +
            "ch.charger_code, s.name as station_name " +
            "FROM charge_records cr " +
            "JOIN users u ON cr.user_id = u.id " +
            "JOIN chargers ch ON cr.charger_id = ch.id " +
            "JOIN stations s ON ch.station_id = s.id " +
            "WHERE cr.id = #{id}")
    Map<String, Object> findEnrichedById(@Param("id") UUID id);

    @Select("SELECT cr.id, cr.user_id, cr.charger_id, cr.start_time, cr.end_time, " +
            "cr.energy_kwh, cr.fee, cr.status, cr.deduction_status, " +
            "u.name as user_name, u.plate_number, " +
            "ch.charger_code, s.name as station_name " +
            "FROM charge_records cr " +
            "JOIN users u ON cr.user_id = u.id " +
            "JOIN chargers ch ON cr.charger_id = ch.id " +
            "JOIN stations s ON ch.station_id = s.id " +
            "WHERE cr.status = #{status} ORDER BY cr.start_time DESC")
    List<Map<String, Object>> findEnrichedByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) as count, COALESCE(SUM(energy_kwh), 0) as total_energy, COALESCE(SUM(fee), 0) as total_fee " +
            "FROM charge_records WHERE charger_id IN (SELECT id FROM chargers WHERE station_id = #{stationId}) " +
            "AND status = 'COMPLETED'")
    Map<String, Object> getStationChargeStats(@Param("stationId") UUID stationId);
}