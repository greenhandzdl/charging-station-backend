package com.charging.infrastructure.security;

import com.charging.entity.ChargeRecord;
import com.charging.mapper.ChargeRecordMapper;
import lombok.RequiredArgsConstructor;
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
    public boolean canStop(JwtUserPrincipal principal, String recordIdStr) {
        if (principal == null || recordIdStr == null) {
            return false;
        }

        // ADMIN and SUPER_ADMIN can stop any record
        if ("ADMIN".equalsIgnoreCase(principal.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(principal.getRole())) {
            return true;
        }

        // Normal users must own the record
        try {
            UUID recordId = UUID.fromString(recordIdStr);
            Optional<ChargeRecord> record = chargeRecordMapper.findById(recordId);
            return record.isPresent()
                    && record.get().getUserId().toString().equals(principal.getUserId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}