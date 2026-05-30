package com.charging.entity;

import com.charging.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class User {
    private UUID id;
    private String name;
    private String phone;
    private String plateNumber;

    @JsonIgnore
    private String passwordHash;

    private UserRole role;
    private BigDecimal balance;

    /** 欠费冻结截止时间，NULL 表示未冻结 */
    private LocalDateTime frozenUntil;

    private Integer failedLoginAttempts;

    /** 账户锁定截止时间，NULL 表示未锁定 */
    private LocalDateTime accountLockedUntil;

    @JsonIgnore
    private String passwordResetToken;

    @JsonIgnore
    private String passwordResetTokenHash;

    @JsonIgnore
    private LocalDateTime resetTokenExpiresAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}