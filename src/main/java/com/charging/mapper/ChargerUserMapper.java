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

    @Select("SELECT * FROM charger_users WHERE phone = #{phone}")
    Optional<ChargerUser> findByPhone(String phone);

    @Select("SELECT * FROM charger_users WHERE charger_id = #{chargerId}")
    Optional<ChargerUser> findByChargerId(UUID chargerId);

    @Select("SELECT * FROM charger_users")
    List<ChargerUser> findAll();

    @Insert("INSERT INTO charger_users (id, charger_id, name, phone, password_hash, identity_type, is_active, allowed_charger_ids, created_at) " +
            "VALUES (#{id}, #{chargerId}, #{name}, #{phone}, #{passwordHash}, #{identityType}, #{isActive}, #{allowedChargerIds}, now())")
    int insert(ChargerUser chargerUser);

    @Update("UPDATE charger_users SET name = #{name}, phone = #{phone}, password_hash = #{passwordHash}, " +
            "identity_type = #{identityType}, is_active = #{isActive}, allowed_charger_ids = #{allowedChargerIds}, " +
            "updated_at = now() WHERE id = #{id}")
    int update(ChargerUser chargerUser);

    @Update("UPDATE charger_users SET last_login_at = now(), updated_at = now() WHERE id = #{id}")
    int updateLastLogin(UUID id);

    @Delete("DELETE FROM charger_users WHERE id = #{id}")
    int deleteById(UUID id);
}