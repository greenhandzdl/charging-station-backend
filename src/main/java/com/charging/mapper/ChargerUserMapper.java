package com.charging.mapper;

import com.charging.entity.ChargerUser;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ChargerUserMapper {

    @Select("SELECT * FROM charger_users WHERE id = #{id}")
    Optional<ChargerUser> findById(UUID id);

    @Select("SELECT * FROM charger_users WHERE login_id = #{loginId}")
    Optional<ChargerUser> findByLoginId(String loginId);

    @Select("SELECT * FROM charger_users WHERE charger_id = #{chargerId}")
    Optional<ChargerUser> findByChargerId(UUID chargerId);

    @Select("SELECT * FROM charger_users WHERE station_id = #{stationId}")
    List<ChargerUser> findByStationId(UUID stationId);

    @Select("SELECT * FROM charger_users WHERE parent_id = #{parentId}")
    List<ChargerUser> findByParentId(UUID parentId);

    @Select("SELECT * FROM charger_users")
    List<ChargerUser> findAll();

    @Insert("INSERT INTO charger_users (id, login_id, name, password_hash, permission_level, charger_id, station_id, parent_id, token_version, created_at) " +
            "VALUES (#{id}, #{loginId}, #{name}, #{passwordHash}, #{permissionLevel}, #{chargerId}, #{stationId}, #{parentId}, COALESCE(#{tokenVersion},0), now())")
    int insert(ChargerUser chargerUser);

    @Update("UPDATE charger_users SET name = #{name}, login_id = #{loginId}, password_hash = #{passwordHash}, " +
            "permission_level = #{permissionLevel}, charger_id = #{chargerId}, station_id = #{stationId}, " +
            "parent_id = #{parentId}, updated_at = now() WHERE id = #{id}")
    int update(ChargerUser chargerUser);

    @Update("UPDATE charger_users SET last_login_at = now(), updated_at = now() WHERE id = #{id}")
    int updateLastLogin(UUID id);

    @Update("UPDATE charger_users SET token_version = token_version + 1, updated_at = now() WHERE id = #{id}")
    int incrementTokenVersion(UUID id);

    @Select("SELECT token_version FROM charger_users WHERE id = #{id}")
    Integer getTokenVersion(UUID id);

    @Delete("DELETE FROM charger_users WHERE id = #{id}")
    int deleteById(UUID id);
}