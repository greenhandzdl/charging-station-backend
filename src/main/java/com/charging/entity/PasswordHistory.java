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
public class PasswordHistory {
    private Long id;
    private UUID userId;
    private String passwordHash;
    private LocalDateTime createdAt;
}
