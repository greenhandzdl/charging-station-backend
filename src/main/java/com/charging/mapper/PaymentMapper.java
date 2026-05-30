package com.charging.mapper;

import com.charging.entity.Payment;
import org.apache.ibatis.annotations.*;

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

    @Select("SELECT status FROM payments WHERE id = #{id}")
    String findStatusById(UUID id);

    @Update("UPDATE payments SET status = 'success', gateway_tx_id = #{txId} WHERE id = #{id} AND status = 'pending'")
    int markSuccess(@Param("id") UUID id, @Param("txId") String txId);

    @Update("UPDATE payments SET status = 'failed' WHERE id = #{id}")
    int markFailed(UUID id);

    // Statistics
    @Select("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'success' AND created_at >= #{start} AND created_at < #{end}")
    java.math.BigDecimal sumSuccessAmountByDateRange(@Param("start") java.time.LocalDateTime start,
                                                     @Param("end") java.time.LocalDateTime end);
}