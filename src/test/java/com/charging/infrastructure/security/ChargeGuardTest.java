package com.charging.infrastructure.security;

import com.charging.entity.ChargeRecord;
import com.charging.mapper.ChargeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeGuardTest {

    @Mock
    private ChargeRecordMapper chargeRecordMapper;

    private ChargeGuard chargeGuard;

    private UUID recordId;
    private UUID userId;
    private ChargeRecord testRecord;

    @BeforeEach
    void setUp() {
        chargeGuard = new ChargeGuard(chargeRecordMapper);
        recordId = UUID.randomUUID();
        userId = UUID.randomUUID();

        testRecord = ChargeRecord.builder()
                .id(recordId)
                .userId(userId)
                .chargerId(UUID.randomUUID())
                .startTime(LocalDateTime.now())
                .build();
    }

    @Test
    void canStop_asAdmin_shouldReturnTrue() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(UUID.randomUUID().toString())
                .role("ADMIN")
                .build();

        assertTrue(chargeGuard.canStop(principal, recordId.toString()));
    }

    @Test
    void canStop_asSuperAdmin_shouldReturnTrue() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(UUID.randomUUID().toString())
                .role("SUPER_ADMIN")
                .build();

        assertTrue(chargeGuard.canStop(principal, recordId.toString()));
    }

    @Test
    void canStop_asOwner_shouldReturnTrue() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(testRecord));

        assertTrue(chargeGuard.canStop(principal, recordId.toString()));
    }

    @Test
    void canStop_asNonOwner_shouldReturnFalse() {
        UUID otherUserId = UUID.randomUUID();
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(otherUserId.toString())
                .role("USER")
                .build();
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(testRecord));

        assertFalse(chargeGuard.canStop(principal, recordId.toString()));
    }

    @Test
    void canStop_withNullPrincipal_shouldReturnFalse() {
        assertFalse(chargeGuard.canStop(null, recordId.toString()));
    }

    @Test
    void canStop_withNullRecordId_shouldReturnFalse() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();

        assertFalse(chargeGuard.canStop(principal, null));
    }

    @Test
    void canStop_withInvalidRecordId_shouldReturnFalse() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();

        assertFalse(chargeGuard.canStop(principal, "not-a-uuid"));
    }

    @Test
    void canStop_withRecordNotFound_shouldReturnFalse() {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.empty());

        assertFalse(chargeGuard.canStop(principal, recordId.toString()));
    }
}