package com.charging.service;

import com.charging.infrastructure.dto.*;
import com.charging.entity.Repair;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RepairService {
    RepairResponse submit(UUID userId, SubmitRepairRequest request);
    List<RepairResponse> listRepairs(UUID userId, String userRole, Map<String, String> params);
    void assign(UUID repairId, AssignRepairRequest request, UUID adminId);
    void resolve(UUID repairId, UUID userId, String userRole);
    void close(UUID repairId, UUID adminId);
    void reject(UUID repairId, RejectRepairRequest request, UUID adminId);
}