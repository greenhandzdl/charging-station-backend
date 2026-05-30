package com.charging.entity;

import com.charging.enums.RepairStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Repair {
    private UUID id;
    private UUID chargerId;
    private UUID reporterId;
    private String description;
    private RepairStatus status;
    private UUID handledBy;
    private LocalDateTime reportedAt;
    private LocalDateTime handledAt;
    private String rejectReason;
}