package com.charging.entity;

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
public class AuditLog {
    private UUID id;
    private UUID actorId;
    private String actorType;
    private String action;
    private String resource;
    private UUID resourceId;
    private String payload;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdAt;
}