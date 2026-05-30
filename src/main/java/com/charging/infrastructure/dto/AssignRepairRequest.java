package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRepairRequest {

    @NotNull(message = "维修人员ID不能为空")
    private UUID handledBy;
}