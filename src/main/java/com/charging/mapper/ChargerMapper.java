package com.charging.mapper;

import com.charging.entity.Charger;
import com.charging.enums.ChargerStatus;
import com.charging.infrastructure.dto.ChargerSuggestDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ChargerMapper {

    @Select("SELECT * FROM chargers WHERE id = #{id}")
    Optional<Charger> findById(UUID id);

    @Select("SELECT * FROM chargers WHERE station_id = #{stationId}")
    List<Charger> findByStationId(UUID stationId);

    @Select("SELECT * FROM chargers")
    List<Charger> findAll();

    @Select("SELECT * FROM chargers WHERE charger_code = #{chargerCode}")
    Optional<Charger> findByChargerCode(String chargerCode);

    @Update("UPDATE chargers SET status = #{status} WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatusConditionally(@Param("id") UUID id,
                                  @Param("status") String status,
                                  @Param("expectedStatus") String expectedStatus);

    @Update("UPDATE chargers SET status = #{status}, updated_at = now() WHERE id = #{id}")
    int updateStatus(@Param("id") UUID id, @Param("status") ChargerStatus status);

    @Select("SELECT status FROM chargers WHERE id = #{id}")
    String findStatusById(UUID id);

    @Insert("INSERT INTO chargers (id, station_id, charger_code, type, status, created_at) " +
            "VALUES (#{id}, #{stationId}, #{chargerCode}, #{type}, #{status}, now())")
    int insert(Charger charger);

    @Update("UPDATE chargers SET station_id = #{stationId}, charger_code = #{chargerCode}, " +
            "type = #{type}, status = #{status}, updated_at = now() WHERE id = #{id}")
    int update(Charger charger);

    @Delete("DELETE FROM chargers WHERE id = #{id}")
    int deleteById(UUID id);

    @Select("SELECT COUNT(*) FROM chargers WHERE station_id = #{stationId} AND status = #{status}")
    int countByStationIdAndStatus(@Param("stationId") UUID stationId, @Param("status") String status);

    @Select("SELECT c.id, c.charger_code AS code, s.name AS station_name, c.status " +
            "FROM chargers c LEFT JOIN stations s ON c.station_id = s.id " +
            "WHERE c.charger_code ILIKE '%' || #{keyword} || '%' OR s.name ILIKE '%' || #{keyword} || '%' " +
            "LIMIT #{limit}")
    List<ChargerSuggestDTO> suggestChargers(@Param("keyword") String keyword, @Param("limit") int limit);
}