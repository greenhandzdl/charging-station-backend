package com.charging.mapper;

import com.charging.entity.ChargerDevice;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ChargerDeviceMapper {

    @Select("SELECT * FROM charger_devices WHERE id = #{id}")
    Optional<ChargerDevice> findById(UUID id);

    @Select("SELECT * FROM charger_devices WHERE charger_id = #{chargerId}")
    Optional<ChargerDevice> findByChargerId(UUID chargerId);

    @Select("SELECT * FROM charger_devices")
    List<ChargerDevice> findAll();

    @Select("SELECT * FROM charger_devices WHERE auth_token = #{authToken}")
    Optional<ChargerDevice> findByAuthToken(String authToken);

    @Insert("INSERT INTO charger_devices (id, charger_id, device_name, device_type, auth_token, serial_number, firmware_version, created_at) " +
            "VALUES (#{id}, #{chargerId}, #{deviceName}, #{deviceType}, #{authToken}, #{serialNumber}, #{firmwareVersion}, now())")
    int insert(ChargerDevice chargerDevice);

    @Update("UPDATE charger_devices SET device_name = #{deviceName}, device_type = #{deviceType}, " +
            "auth_token = #{authToken}, serial_number = #{serialNumber}, firmware_version = #{firmwareVersion}, " +
            "last_online_at = now(), updated_at = now() WHERE id = #{id}")
    int update(ChargerDevice chargerDevice);

    @Delete("DELETE FROM charger_devices WHERE id = #{id}")
    int deleteById(UUID id);

    @Update("UPDATE charger_devices SET last_online_at = now(), updated_at = now() WHERE id = #{id}")
    int updateLastOnline(UUID id);
}