package com.charging.mapper;

import com.charging.entity.PasswordHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PasswordHistoryMapper {

    @Select("SELECT * FROM password_history WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT 3")
    List<PasswordHistory> findRecentByUserId(UUID userId);

    @Insert("INSERT INTO password_history (user_id, password_hash, created_at) VALUES (#{userId}, #{passwordHash}, now())")
    int insert(PasswordHistory history);

    @Delete("DELETE FROM password_history WHERE user_id = #{userId} AND id NOT IN (" +
            "SELECT id FROM password_history WHERE user_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT 3" +
            ")")
    int deleteOldRecords(UUID userId);
}
