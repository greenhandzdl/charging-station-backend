package com.charging.mapper;

import com.charging.entity.Payment;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PaymentMapper {

    @Insert("INSERT INTO payments (id, user_id, charge_record_id, method, amount, status, gateway_tx_id, created_at) " +
            "VALUES (#{id}, #{userId}, #{chargeRecordId}, #{method}, #{amount}, #{status}, #{gatewayTxId}, now())")
    int insert(Payment payment);

    @Select("SELECT * FROM payments WHERE gateway_tx_id = #{txId}")
    Optional<Payment> findByGatewayTxId(String txId);

    @Select("SELECT * FROM payments WHERE id = #{id}")
    Optional<Payment> findById(UUID id);

    @Select("SELECT * FROM payments WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Payment> findByUserId(UUID userId);

    @Select("SELECT * FROM payments ORDER BY created_at DESC")
    List<Payment> findAll();

    @Select("SELECT * FROM payments WHERE user_id = #{userId} AND method NOT IN ('auto_deduct', 'SYSTEM') ORDER BY created_at DESC")
    List<Payment> findRechargesByUserId(UUID userId);

    @Select("SELECT * FROM payments WHERE method NOT IN ('auto_deduct', 'SYSTEM') ORDER BY created_at DESC")
    List<Payment> findAllRecharges();

    @Select("SELECT * FROM payments WHERE user_id = #{userId} AND method IN ('auto_deduct', 'SYSTEM') ORDER BY created_at DESC")
    List<Payment> findDeductionsByUserId(UUID userId);

    @Select("SELECT * FROM payments WHERE method IN ('auto_deduct', 'SYSTEM') ORDER BY created_at DESC")
    List<Payment> findAllDeductions();

    // ---- Filtered queries for recharge history ----
    @Select("<script>" +
            "SELECT * FROM payments WHERE method NOT IN ('auto_deduct', 'SYSTEM') " +
            "<if test='userId != null'>AND user_id = #{userId} </if>" +
            "<if test='startTime != null'>AND created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND created_at &lt;= #{endTime} </if>" +
            "<if test='minAmount != null'>AND amount &gt;= #{minAmount} </if>" +
            "<if test='maxAmount != null'>AND amount &lt;= #{maxAmount} </if>" +
            "<if test='status != null'>AND status = #{status} </if>" +
            "<if test='keyword != null'>AND (CAST(method AS text) ILIKE '%' || #{keyword} || '%') </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<Payment> findRechargesFiltered(@Param("userId") UUID userId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("minAmount") BigDecimal minAmount,
                                         @Param("maxAmount") BigDecimal maxAmount,
                                         @Param("status") String status,
                                         @Param("keyword") String keyword);

    // ---- Filtered queries for deduction records (auto_deduct only) ----
    @Select("<script>" +
            "SELECT * FROM payments WHERE method = 'auto_deduct' " +
            "<if test='userId != null'>AND user_id = #{userId} </if>" +
            "<if test='startTime != null'>AND created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND created_at &lt;= #{endTime} </if>" +
            "<if test='minAmount != null'>AND amount &gt;= #{minAmount} </if>" +
            "<if test='maxAmount != null'>AND amount &lt;= #{maxAmount} </if>" +
            "<if test='status != null'>AND status = #{status} </if>" +
            "<if test='keyword != null'>AND (CAST(method AS text) ILIKE '%' || #{keyword} || '%') </if>" +
            "ORDER BY created_at DESC" +
            "</script>")
    List<Payment> findDeductionsFiltered(@Param("userId") UUID userId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("minAmount") BigDecimal minAmount,
                                          @Param("maxAmount") BigDecimal maxAmount,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword);

    @Select("SELECT status FROM payments WHERE id = #{id}")
    String findStatusById(UUID id);

    @Select("SELECT * FROM payments WHERE status = #{status} ORDER BY created_at DESC")
    List<Payment> findByStatus(String status);

    @Update("UPDATE payments SET status = 'SUCCESS', gateway_tx_id = #{txId} WHERE id = #{id} AND status = 'PENDING'")
    int markSuccess(@Param("id") UUID id, @Param("txId") String txId);

    @Update("UPDATE payments SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Update("UPDATE payments SET status = 'FAILED' WHERE id = #{id}")
    int markFailed(UUID id);

    // Statistics
    @Select("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS' AND created_at >= #{start} AND created_at < #{end}")
    java.math.BigDecimal sumSuccessAmountByDateRange(@Param("start") java.time.LocalDateTime start,
                                                     @Param("end") java.time.LocalDateTime end);
}