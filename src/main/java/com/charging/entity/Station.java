package com.charging.entity;

import com.charging.enums.StationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Station {
    private UUID id;
    private String name;
    private String location;
    private Integer chargerCount;
    private StationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}