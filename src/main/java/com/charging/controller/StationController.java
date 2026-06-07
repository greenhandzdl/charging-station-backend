package com.charging.controller;

import com.charging.entity.Charger;
import com.charging.entity.Station;
import com.charging.infrastructure.dto.ChargerRequest;
import com.charging.infrastructure.dto.ChargerSuggestDTO;
import com.charging.infrastructure.dto.StationRequest;
import com.charging.infrastructure.dto.StationSuggestDTO;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.StationMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StationController {

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;

    // ===== Station CRUD =====

    @GetMapping("/stations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Station>> listStations() {
        return ResponseEntity.ok(stationMapper.findAll());
    }

    @GetMapping("/stations/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Station>> searchStations(@RequestParam String name) {
        return ResponseEntity.ok(stationMapper.searchByName(name));
    }

    @GetMapping("/stations/search/suggest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StationSuggestDTO>> suggestStations(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(stationMapper.suggestStations(keyword, Math.min(limit, 20)));
    }

    @GetMapping("/chargers/search/suggest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChargerSuggestDTO>> suggestChargers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(chargerMapper.suggestChargers(keyword, Math.min(limit, 20)));
    }

    @GetMapping("/stations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Station> getStation(@PathVariable UUID id) {
        return stationMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/stations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Station> createStation(@Valid @RequestBody StationRequest request) {
        Station station = Station.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .location(request.getLocation())
                .chargerCount(request.getChargerCount() != null ? request.getChargerCount() : 0)
                .status(request.getStatus() != null
                        ? com.charging.enums.StationStatus.valueOf(request.getStatus().toUpperCase())
                        : com.charging.enums.StationStatus.NORMAL)
                .createdAt(LocalDateTime.now())
                .build();
        stationMapper.insert(station);
        return ResponseEntity.status(HttpStatus.CREATED).body(station);
    }

    @PutMapping("/stations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Station> updateStation(@PathVariable UUID id,
                                                 @Valid @RequestBody StationRequest request) {
        Station station = stationMapper.findById(id)
                .orElseThrow(() -> new RuntimeException("Station not found"));
        station.setName(request.getName());
        station.setLocation(request.getLocation());
        station.setChargerCount(request.getChargerCount() != null ? request.getChargerCount() : station.getChargerCount());
        station.setStatus(request.getStatus() != null
                ? com.charging.enums.StationStatus.valueOf(request.getStatus().toUpperCase())
                : station.getStatus());
        station.setUpdatedAt(LocalDateTime.now());
        stationMapper.update(station);
        return ResponseEntity.ok(station);
    }

    @DeleteMapping("/stations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteStation(@PathVariable UUID id) {
        stationMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    // ===== Charger CRUD =====

    @GetMapping("/chargers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Charger>> listChargers(@RequestParam(required = false) UUID stationId) {
        if (stationId != null) {
            return ResponseEntity.ok(chargerMapper.findByStationId(stationId));
        }
        return ResponseEntity.ok(chargerMapper.findAll());
    }

    @GetMapping("/chargers/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Charger> getCharger(@PathVariable UUID id) {
        return chargerMapper.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/chargers/by-code/{code}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Charger> getChargerByCode(@PathVariable String code) {
        return chargerMapper.findByChargerCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/chargers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Charger> createCharger(@Valid @RequestBody ChargerRequest request) {
        Charger charger = Charger.builder()
                .id(UUID.randomUUID())
                .stationId(request.getStationId())
                .chargerCode(request.getChargerCode())
                .type(com.charging.enums.ChargerType.valueOf(request.getType().toUpperCase()))
                .status(request.getStatus() != null
                        ? com.charging.enums.ChargerStatus.valueOf(request.getStatus().toUpperCase())
                        : com.charging.enums.ChargerStatus.IDLE)
                .createdAt(LocalDateTime.now())
                .build();
        chargerMapper.insert(charger);

        // Update station charger count
        stationMapper.findById(request.getStationId()).ifPresent(station -> {
            station.setChargerCount(station.getChargerCount() + 1);
            station.setUpdatedAt(LocalDateTime.now());
            stationMapper.update(station);
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(charger);
    }

    @PutMapping("/chargers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'MAINTAINER')")
    public ResponseEntity<Charger> updateCharger(@PathVariable UUID id,
                                                 @Valid @RequestBody ChargerRequest request) {
        Charger charger = chargerMapper.findById(id)
                .orElseThrow(() -> new RuntimeException("Charger not found"));
        charger.setStationId(request.getStationId());
        charger.setChargerCode(request.getChargerCode());
        charger.setType(com.charging.enums.ChargerType.valueOf(request.getType().toUpperCase()));
        if (request.getStatus() != null) {
            charger.setStatus(com.charging.enums.ChargerStatus.valueOf(request.getStatus().toUpperCase()));
        }
        charger.setUpdatedAt(LocalDateTime.now());
        chargerMapper.update(charger);
        return ResponseEntity.ok(charger);
    }

    @DeleteMapping("/chargers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCharger(@PathVariable UUID id) {
        chargerMapper.findById(id).ifPresent(charger -> {
            stationMapper.findById(charger.getStationId()).ifPresent(station -> {
                station.setChargerCount(Math.max(0, station.getChargerCount() - 1));
                station.setUpdatedAt(LocalDateTime.now());
                stationMapper.update(station);
            });
        });
        chargerMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }
}