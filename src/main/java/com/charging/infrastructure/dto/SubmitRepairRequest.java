package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRepairRequest {

    @NotBlank(message = "充电桩ID不能为空")
    private UUID chargerId;

    @NotBlank(message = "故障描述不能为空")
    private String description;
}