package com.charging.infrastructure.security;

import com.charging.entity.ChargeRecord;
import com.charging.mapper.ChargeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * ChargeGuard bean for @PreAuthorize annotation-level authorization.
 * Used by ChargingController.stopCharge to verify record ownership.
 */
@Component("chargeGuard")
@RequiredArgsConstructor
public class ChargeGuard {

    private final ChargeRecordMapper chargeRecordMapper;

    /**
     * Check if the authenticated user can stop the given charge record.
     * - Normal users can only stop their own records
     * - ADMIN/SUPER_ADMIN can stop any record
     */
    public boolean canStop(Authentication authentication, UUID recordId) {
        if (authentication == null || recordId == null) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof JwtUserPrincipal jwtPrincipal)) {
            return false;
        }

        // ADMIN and SUPER_ADMIN can stop any record
        if ("ADMIN".equalsIgnoreCase(jwtPrincipal.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(jwtPrincipal.getRole())) {
            return true;
        }

        // Normal users must own the record
        try {
            Optional<ChargeRecord> record = chargeRecordMapper.findById(recordId);
            return record.isPresent()
                    && record.get().getUserId().toString().equals(jwtPrincipal.getUserId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}