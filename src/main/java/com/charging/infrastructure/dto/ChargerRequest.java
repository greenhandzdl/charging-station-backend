package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
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
public class ChargerRequest {

    @NotNull(message = "充电站ID不能为空")
    private UUID stationId;

    @NotBlank(message = "充电桩编码不能为空")
    private String chargerCode;

    @NotBlank(message = "充电桩类型不能为空")
    private String type;

    private String status;
}