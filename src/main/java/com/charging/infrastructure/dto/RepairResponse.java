package com.charging.infrastructure.dto;

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
public class RepairResponse {

    private UUID id;
    private UUID chargerId;
    private String chargerCode;
    private UUID reporterId;
    private String reporterName;
    private String description;
    private String status;
    private UUID handledBy;
    private String handlerName;
    private LocalDateTime reportedAt;
    private LocalDateTime handledAt;
    private String rejectReason;
}