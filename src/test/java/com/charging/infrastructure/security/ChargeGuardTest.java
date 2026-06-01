package com.charging.infrastructure.security;

import com.charging.entity.ChargeRecord;
import com.charging.mapper.ChargeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
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

    private Authentication authWithRole(String role, String uid) {
        JwtUserPrincipal principal = JwtUserPrincipal.builder()
                .userId(uid)
                .role(role)
                .build();
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void canStop_asAdmin_shouldReturnTrue() {
        assertTrue(chargeGuard.canStop(authWithRole("ADMIN", UUID.randomUUID().toString()), recordId));
    }

    @Test
    void canStop_asSuperAdmin_shouldReturnTrue() {
        assertTrue(chargeGuard.canStop(authWithRole("SUPER_ADMIN", UUID.randomUUID().toString()), recordId));
    }

    @Test
    void canStop_asOwner_shouldReturnTrue() {
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(testRecord));
        assertTrue(chargeGuard.canStop(authWithRole("USER", userId.toString()), recordId));
    }

    @Test
    void canStop_asNonOwner_shouldReturnFalse() {
        UUID otherUserId = UUID.randomUUID();
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.of(testRecord));
        assertFalse(chargeGuard.canStop(authWithRole("USER", otherUserId.toString()), recordId));
    }

    @Test
    void canStop_withNullAuth_shouldReturnFalse() {
        assertFalse(chargeGuard.canStop(null, recordId));
    }

    @Test
    void canStop_withNullRecordId_shouldReturnFalse() {
        assertFalse(chargeGuard.canStop(authWithRole("USER", userId.toString()), null));
    }

    @Test
    void canStop_withRecordNotFound_shouldReturnFalse() {
        when(chargeRecordMapper.findById(recordId)).thenReturn(Optional.empty());
        assertFalse(chargeGuard.canStop(authWithRole("USER", userId.toString()), recordId));
    }
}