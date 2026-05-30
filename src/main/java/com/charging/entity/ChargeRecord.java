package com.charging.entity;

import com.charging.enums.RecordStatus;
import com.charging.enums.DeductionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeRecord {
    private UUID id;
    private UUID userId;
    private UUID chargerId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal energyKwh;
    private BigDecimal fee;
    private RecordStatus status;
    private DeductionStatus deductionStatus;
    private LocalDateTime createdAt;
}