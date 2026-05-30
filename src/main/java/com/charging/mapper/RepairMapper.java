package com.charging.mapper;

import com.charging.entity.Repair;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface RepairMapper {

    @Insert("INSERT INTO repairs (id, charger_id, reporter_id, description, status, reported_at) " +
            "VALUES (#{id}, #{chargerId}, #{reporterId}, #{description}, 'open', now())")
    int insert(Repair repair);

    @Select("SELECT * FROM repairs WHERE id = #{id} FOR UPDATE")
    Optional<Repair> findByIdWithLock(UUID id);

    @Select("SELECT * FROM repairs WHERE id = #{id}")
    Optional<Repair> findById(UUID id);

    @Select("SELECT * FROM repairs WHERE reporter_id = #{reporterId} ORDER BY reported_at DESC")
    List<Repair> findByReporterId(UUID reporterId);

    @Select("SELECT * FROM repairs ORDER BY reported_at DESC")
    List<Repair> findAll();

    @Select("SELECT * FROM repairs WHERE status = #{status} ORDER BY reported_at DESC")
    List<Repair> findByStatus(String status);

    @Update("UPDATE repairs SET status = #{status}, handled_by = #{userId}, handled_at = now() WHERE id = #{id}")
    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("userId") UUID userId);

    @Update("UPDATE repairs SET status = 'in_progress', handled_by = #{userId} WHERE id = #{id} AND status = 'open'")
    int assign(@Param("id") UUID id, @Param("userId") UUID userId);

    @Update("UPDATE repairs SET status = 'resolved' WHERE id = #{id} AND status = 'in_progress'")
    int resolve(UUID id);

    @Update("UPDATE repairs SET status = 'closed', handled_at = now() WHERE id = #{id} AND status IN ('open', 'resolved')")
    int close(UUID id);

    @Update("UPDATE repairs SET status = 'in_progress', reject_reason = #{reason} WHERE id = #{id} AND status = 'resolved'")
    int reject(@Param("id") UUID id, @Param("reason") String reason);

    @Update("UPDATE repairs SET handled_at = now() WHERE id = #{id}")
    int markHandled(UUID id);
}