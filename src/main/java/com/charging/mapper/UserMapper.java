package com.charging.mapper;

import com.charging.entity.User;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE phone = #{phone}")
    Optional<User> findByPhone(String phone);

    @Select("SELECT * FROM users WHERE id = #{id}")
    Optional<User> findById(UUID id);

    @Select("SELECT * FROM users WHERE password_reset_token_hash = #{hash} AND reset_token_expires_at > now()")
    Optional<User> findByPasswordResetTokenHash(String hash);

    @Insert("INSERT INTO users (id, name, phone, plate_number, password_hash, role, balance, failed_login_attempts, created_at) " +
            "VALUES (#{id}, #{name}, #{phone}, #{plateNumber}, #{passwordHash}, #{role}, #{balance}, #{failedLoginAttempts}, now())")
    int insert(User user);

    @Update("UPDATE users SET balance = balance - #{amount} WHERE id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") UUID userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE users SET balance = balance + #{amount} WHERE id = #{userId}")
    int addBalance(@Param("userId") UUID userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE phone = #{phone}")
    int incrementFailedAttempts(String phone);

    @Update("UPDATE users SET failed_login_attempts = 0, account_locked_until = NULL WHERE phone = #{phone}")
    int resetFailedAttempts(String phone);

    @Update("UPDATE users SET failed_login_attempts = 0, account_locked_until = NULL WHERE id = #{id}")
    int resetFailedAttemptsById(UUID id);

    @Update("UPDATE users SET account_locked_until = now() + INTERVAL '30 minutes' WHERE id = #{id}")
    int lockAccount(UUID id);

    @Update("UPDATE users SET password_hash = #{newPasswordHash}, password_reset_token = NULL, " +
            "password_reset_token_hash = NULL, reset_token_expires_at = NULL WHERE id = #{id}")
    int updatePassword(@Param("id") UUID id, @Param("newPasswordHash") String newPasswordHash);

    @Update("UPDATE users SET password_reset_token = #{tokenHash}, " +
            "password_reset_token_hash = #{tokenPlainHash}, " +
            "reset_token_expires_at = now() + INTERVAL '15 minutes' WHERE id = #{id}")
    int setPasswordResetToken(@Param("id") UUID id,
                              @Param("tokenHash") String tokenHash,
                              @Param("tokenPlainHash") String tokenPlainHash);

    @Update("UPDATE users SET password_reset_token = NULL, password_reset_token_hash = NULL, reset_token_expires_at = NULL WHERE id = #{id}")
    int clearPasswordResetToken(UUID id);

    @Update("UPDATE users SET frozen_until = now() + INTERVAL '30 days' WHERE id = #{userId}")
    int freezeAccount(UUID userId);

    @Update("UPDATE users SET frozen_until = NULL WHERE id = #{userId} AND frozen_until IS NOT NULL")
    int unfreezeAccount(UUID userId);

    @Select("SELECT balance FROM users WHERE id = #{id}")
    BigDecimal findBalanceById(UUID id);

    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    Optional<User> findByIdWithLock(UUID id);

    @Select("SELECT * FROM users")
    List<User> findAll();

    @Update("<script>" +
            "UPDATE users SET " +
            "name = #{user.name, jdbcType=VARCHAR}, " +
            "plate_number = #{user.plateNumber, jdbcType=VARCHAR}, " +
            "<if test='user.role != null'>role = #{user.role, jdbcType=VARCHAR}, </if>" +
            "updated_at = now() " +
            "WHERE id = #{user.id}" +
            "</script>")
    int update(@Param("user") User user);

    @Select("SELECT * FROM users WHERE name ILIKE '%' || #{keyword} || '%' OR phone ILIKE '%' || #{keyword} || '%' ORDER BY name ASC")
    List<User> searchByKeyword(@Param("keyword") String keyword);
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(UUID id);
}