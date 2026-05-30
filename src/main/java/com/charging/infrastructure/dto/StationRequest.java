package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationRequest {

    @NotBlank(message = "充电站名称不能为空")
    private String name;

    private String location;

    private Integer chargerCount;

    private String status;
}