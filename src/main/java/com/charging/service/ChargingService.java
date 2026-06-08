package com.charging.service;

import com.charging.infrastructure.dto.ChargeResponse;
import com.charging.infrastructure.dto.StartChargeRequest;
import com.charging.infrastructure.dto.StopChargeRequest;
import com.charging.infrastructure.dto.ForceStopRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChargingService {
    ChargeResponse startCharge(UUID userId, StartChargeRequest request);
    ChargeResponse stopCharge(UUID userId, String userRole, StopChargeRequest request);
    ChargeResponse forceStop(UUID adminId, UUID recordId, ForceStopRequest request, String clientIp);
    List<Map<String, Object>> queryCharges(UUID userId, String userRole, Map<String, String> params);
    int autoStopOnInsufficientBalance();
}