package com.charging.mapper;

import com.charging.entity.Station;
import com.charging.infrastructure.dto.StationSuggestDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface StationMapper {

    @Select("SELECT * FROM stations WHERE id = #{id}")
    Optional<Station> findById(UUID id);

    @Select("SELECT * FROM stations")
    List<Station> findAll();

    @Insert("INSERT INTO stations (id, name, location, charger_count, status, created_at) " +
            "VALUES (#{id}, #{name}, #{location}, #{chargerCount}, #{status}, now())")
    int insert(Station station);

    @Update("UPDATE stations SET name = #{name}, location = #{location}, " +
            "charger_count = #{chargerCount}, status = #{status}, updated_at = now() WHERE id = #{id}")
    int update(Station station);

    @Select("SELECT * FROM stations WHERE name ILIKE '%' || #{name} || '%'")
    List<Station> searchByName(String name);

    @Select("SELECT id, name, location AS address FROM stations WHERE name ILIKE '%' || #{keyword} || '%' OR location ILIKE '%' || #{keyword} || '%' LIMIT #{limit}")
    List<StationSuggestDTO> suggestStations(@Param("keyword") String keyword, @Param("limit") int limit);

    @Delete("DELETE FROM stations WHERE id = #{id}")
    int deleteById(UUID id);
}