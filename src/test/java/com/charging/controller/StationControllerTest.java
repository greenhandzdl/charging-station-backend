package com.charging.controller;

import com.charging.entity.Charger;
import com.charging.entity.Station;
import com.charging.enums.ChargerStatus;
import com.charging.enums.ChargerType;
import com.charging.enums.StationStatus;
import com.charging.infrastructure.dto.ChargerRequest;
import com.charging.infrastructure.dto.ChargerSuggestDTO;
import com.charging.infrastructure.dto.StationRequest;
import com.charging.infrastructure.dto.StationSuggestDTO;
import com.charging.mapper.ChargerMapper;
import com.charging.mapper.StationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationControllerTest {

    @Mock
    private StationMapper stationMapper;

    @Mock
    private ChargerMapper chargerMapper;

    private StationController stationController;

    private UUID stationId;
    private UUID chargerId;
    private Station testStation;
    private Charger testCharger;

    @BeforeEach
    void setUp() {
        stationController = new StationController(stationMapper, chargerMapper);

        stationId = UUID.randomUUID();
        chargerId = UUID.randomUUID();

        testStation = Station.builder()
                .id(stationId)
                .name("测试充电站")
                .location("北京市海淀区")
                .chargerCount(5)
                .status(StationStatus.NORMAL)
                .createdAt(LocalDateTime.now())
                .build();

        testCharger = Charger.builder()
                .id(chargerId)
                .stationId(stationId)
                .chargerCode("C001")
                .type(ChargerType.FAST)
                .status(ChargerStatus.IDLE)
                .onlineStatus("ONLINE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== Station Tests ====================

    @Test
    void listStations_shouldReturn200WithList() {
        List<Station> expectedStations = List.of(testStation);
        when(stationMapper.findAll()).thenReturn(expectedStations);

        ResponseEntity<List<Station>> response = stationController.listStations();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        assertEquals("测试充电站", response.getBody().get(0).getName());
        verify(stationMapper).findAll();
    }

    @Test
    void listStations_whenEmpty_shouldReturn200WithEmptyList() {
        when(stationMapper.findAll()).thenReturn(List.of());

        ResponseEntity<List<Station>> response = stationController.listStations();

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isEmpty());
        verify(stationMapper).findAll();
    }

    @Test
    void searchStations_byName_shouldReturn200WithFilteredList() {
        List<Station> expectedStations = List.of(testStation);
        when(stationMapper.searchByName("测试")).thenReturn(expectedStations);

        ResponseEntity<List<Station>> response = stationController.searchStations("测试");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        verify(stationMapper).searchByName("测试");
    }

    @Test
    void searchStations_withNoMatch_shouldReturnEmptyList() {
        when(stationMapper.searchByName("不存在")).thenReturn(List.of());

        ResponseEntity<List<Station>> response = stationController.searchStations("不存在");

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isEmpty());
        verify(stationMapper).searchByName("不存在");
    }

    @Test
    void suggestStations_shouldReturn200WithSuggestions() {
        StationSuggestDTO suggestion = StationSuggestDTO.builder()
                .id(stationId)
                .name("测试充电站")
                .address("北京市海淀区")
                .build();
        when(stationMapper.suggestStations("测试", 5)).thenReturn(List.of(suggestion));

        ResponseEntity<List<StationSuggestDTO>> response = stationController.suggestStations("测试", 5);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        assertEquals("测试充电站", response.getBody().get(0).getName());
        verify(stationMapper).suggestStations("测试", 5);
    }

    @Test
    void suggestStations_shouldEnforceLimitOf20() {
        StationSuggestDTO suggestion = StationSuggestDTO.builder()
                .id(stationId)
                .name("测试充电站")
                .address("北京市海淀区")
                .build();
        when(stationMapper.suggestStations("测试", 20)).thenReturn(List.of(suggestion));

        ResponseEntity<List<StationSuggestDTO>> response = stationController.suggestStations("测试", 999);

        assertEquals(200, response.getStatusCodeValue());
        verify(stationMapper).suggestStations("测试", 20);
    }

    @Test
    void getStation_withValidId_shouldReturn200() {
        when(stationMapper.findById(stationId)).thenReturn(Optional.of(testStation));

        ResponseEntity<Station> response = stationController.getStation(stationId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(stationId, response.getBody().getId());
        assertEquals("测试充电站", response.getBody().getName());
        verify(stationMapper).findById(stationId);
    }

    @Test
    void getStation_withInvalidId_shouldReturn404() {
        UUID nonExistentId = UUID.randomUUID();
        when(stationMapper.findById(nonExistentId)).thenReturn(Optional.empty());

        ResponseEntity<Station> response = stationController.getStation(nonExistentId);

        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getBody());
        verify(stationMapper).findById(nonExistentId);
    }

    @Test
    void createStation_asAdmin_shouldReturn201() {
        StationRequest request = StationRequest.builder()
                .name("新充电站")
                .location("上海市浦东新区")
                .chargerCount(10)
                .status("NORMAL")
                .build();

        ResponseEntity<Station> response = stationController.createStation(request);

        assertEquals(201, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("新充电站", response.getBody().getName());
        assertEquals("上海市浦东新区", response.getBody().getLocation());
        assertEquals(10, response.getBody().getChargerCount());
        assertEquals(StationStatus.NORMAL, response.getBody().getStatus());
        assertNotNull(response.getBody().getId());
        assertNotNull(response.getBody().getCreatedAt());

        ArgumentCaptor<Station> captor = ArgumentCaptor.forClass(Station.class);
        verify(stationMapper).insert(captor.capture());
        Station captured = captor.getValue();
        assertEquals("新充电站", captured.getName());
    }

    @Test
    void createStation_withDefaultValues_shouldReturn201() {
        StationRequest request = StationRequest.builder()
                .name("默认充电站")
                .location("深圳市")
                .build();

        ResponseEntity<Station> response = stationController.createStation(request);

        assertEquals(201, response.getStatusCodeValue());
        assertEquals(Integer.valueOf(0), response.getBody().getChargerCount());
        assertEquals(StationStatus.NORMAL, response.getBody().getStatus());
        verify(stationMapper).insert(any(Station.class));
    }

    @Test
    void updateStation_withValidId_shouldReturn200() {
        StationRequest request = StationRequest.builder()
                .name("更新后的充电站")
                .location("杭州市西湖区")
                .chargerCount(8)
                .status("MAINTENANCE")
                .build();

        when(stationMapper.findById(stationId)).thenReturn(Optional.of(testStation));

        ResponseEntity<Station> response = stationController.updateStation(stationId, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("更新后的充电站", response.getBody().getName());
        assertEquals("杭州市西湖区", response.getBody().getLocation());
        assertEquals(8, response.getBody().getChargerCount());
        assertEquals(StationStatus.MAINTENANCE, response.getBody().getStatus());
        verify(stationMapper).findById(stationId);
        verify(stationMapper).update(any(Station.class));
    }

    @Test
    void updateStation_withNonExistentId_shouldThrowRuntimeException() {
        StationRequest request = StationRequest.builder()
                .name("不存在的充电站")
                .build();
        UUID nonExistentId = UUID.randomUUID();

        when(stationMapper.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> stationController.updateStation(nonExistentId, request));
        verify(stationMapper, never()).update(any());
    }

    @Test
    void deleteStation_withValidId_shouldReturn200() {
        when(stationMapper.deleteById(stationId)).thenReturn(1);

        ResponseEntity<Map<String, String>> response = stationController.deleteStation(stationId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("删除成功", response.getBody().get("message"));
        verify(stationMapper).deleteById(stationId);
    }

    // ==================== Charger Tests ====================

    @Test
    void listChargers_withStationId_shouldReturn200WithList() {
        List<Charger> expectedChargers = List.of(testCharger);
        when(chargerMapper.findByStationId(stationId)).thenReturn(expectedChargers);

        ResponseEntity<List<Charger>> response = stationController.listChargers(stationId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        assertEquals("C001", response.getBody().get(0).getChargerCode());
        verify(chargerMapper).findByStationId(stationId);
        verify(chargerMapper, never()).findAll();
    }

    @Test
    void listChargers_withoutStationId_shouldReturnAll() {
        List<Charger> allChargers = List.of(testCharger);
        when(chargerMapper.findAll()).thenReturn(allChargers);

        ResponseEntity<List<Charger>> response = stationController.listChargers(null);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        verify(chargerMapper).findAll();
        verify(chargerMapper, never()).findByStationId(any());
    }

    @Test
    void getCharger_withValidId_shouldReturn200() {
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));

        ResponseEntity<Charger> response = stationController.getCharger(chargerId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(chargerId, response.getBody().getId());
        verify(chargerMapper).findById(chargerId);
    }

    @Test
    void getCharger_withInvalidId_shouldReturn404() {
        UUID nonExistentId = UUID.randomUUID();
        when(chargerMapper.findById(nonExistentId)).thenReturn(Optional.empty());

        ResponseEntity<Charger> response = stationController.getCharger(nonExistentId);

        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getBody());
        verify(chargerMapper).findById(nonExistentId);
    }

    @Test
    void getChargerByCode_withValidCode_shouldReturn200() {
        when(chargerMapper.findByChargerCode("C001")).thenReturn(Optional.of(testCharger));

        ResponseEntity<Charger> response = stationController.getChargerByCode("C001");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(chargerId, response.getBody().getId());
        verify(chargerMapper).findByChargerCode("C001");
    }

    @Test
    void getChargerByCode_withInvalidCode_shouldReturn404() {
        when(chargerMapper.findByChargerCode("UNKNOWN")).thenReturn(Optional.empty());

        ResponseEntity<Charger> response = stationController.getChargerByCode("UNKNOWN");

        assertEquals(404, response.getStatusCodeValue());
        assertNull(response.getBody());
        verify(chargerMapper).findByChargerCode("UNKNOWN");
    }

    @Test
    void suggestChargers_shouldReturn200WithSuggestions() {
        ChargerSuggestDTO suggestion = ChargerSuggestDTO.builder()
                .id(chargerId)
                .code("C001")
                .stationName("测试充电站")
                .status("IDLE")
                .build();
        when(chargerMapper.suggestChargers("C001", 5)).thenReturn(List.of(suggestion));

        ResponseEntity<List<ChargerSuggestDTO>> response = stationController.suggestChargers("C001", 5);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, response.getBody().size());
        assertEquals("C001", response.getBody().get(0).getCode());
        verify(chargerMapper).suggestChargers("C001", 5);
    }

    @Test
    void suggestChargers_shouldEnforceLimitOf20() {
        when(chargerMapper.suggestChargers("C", 20)).thenReturn(List.of());

        ResponseEntity<List<ChargerSuggestDTO>> response = stationController.suggestChargers("C", 999);

        assertEquals(200, response.getStatusCodeValue());
        verify(chargerMapper).suggestChargers("C", 20);
    }

    @Test
    void createCharger_withValidRequest_shouldReturn201() {
        ChargerRequest request = ChargerRequest.builder()
                .stationId(stationId)
                .chargerCode("C002")
                .type("FAST")
                .status("IDLE")
                .build();

        when(stationMapper.findById(stationId)).thenReturn(Optional.of(testStation));

        ResponseEntity<Charger> response = stationController.createCharger(request);

        assertEquals(201, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("C002", response.getBody().getChargerCode());
        assertEquals(ChargerType.FAST, response.getBody().getType());
        assertEquals(ChargerStatus.IDLE, response.getBody().getStatus());

        ArgumentCaptor<Charger> captor = ArgumentCaptor.forClass(Charger.class);
        verify(chargerMapper).insert(captor.capture());
        Charger captured = captor.getValue();
        assertEquals("C002", captured.getChargerCode());

        // Station charger count should be incremented
        verify(stationMapper).findById(stationId);
        verify(stationMapper).update(any(Station.class));
    }

    @Test
    void createCharger_withDefaultStatus_shouldReturn201() {
        ChargerRequest request = ChargerRequest.builder()
                .stationId(stationId)
                .chargerCode("C003")
                .type("SLOW")
                .build();

        when(stationMapper.findById(stationId)).thenReturn(Optional.of(testStation));

        ResponseEntity<Charger> response = stationController.createCharger(request);

        assertEquals(201, response.getStatusCodeValue());
        assertEquals(ChargerStatus.IDLE, response.getBody().getStatus());
        verify(chargerMapper).insert(any(Charger.class));
        verify(stationMapper).findById(stationId);
        verify(stationMapper).update(any(Station.class));
    }

    @Test
    void updateCharger_withValidRequest_shouldReturn200() {
        ChargerRequest request = ChargerRequest.builder()
                .stationId(stationId)
                .chargerCode("C001-UPDATED")
                .type("FAST")
                .status("FAULT")
                .build();

        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));

        ResponseEntity<Charger> response = stationController.updateCharger(chargerId, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("C001-UPDATED", response.getBody().getChargerCode());
        assertEquals(ChargerType.FAST, response.getBody().getType());
        assertEquals(ChargerStatus.FAULT, response.getBody().getStatus());
        verify(chargerMapper).findById(chargerId);
        verify(chargerMapper).update(any(Charger.class));
    }

    @Test
    void updateCharger_withNonExistentId_shouldThrowRuntimeException() {
        ChargerRequest request = ChargerRequest.builder()
                .stationId(stationId)
                .chargerCode("C001")
                .type("FAST")
                .build();
        UUID nonExistentId = UUID.randomUUID();

        when(chargerMapper.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> stationController.updateCharger(nonExistentId, request));
        verify(chargerMapper, never()).update(any());
    }

    @Test
    void deleteCharger_withValidId_shouldReturn200() {
        when(chargerMapper.findById(chargerId)).thenReturn(Optional.of(testCharger));
        when(stationMapper.findById(stationId)).thenReturn(Optional.of(testStation));

        ResponseEntity<Map<String, String>> response = stationController.deleteCharger(chargerId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("删除成功", response.getBody().get("message"));
        verify(chargerMapper).findById(chargerId);
        verify(stationMapper).findById(stationId);
        verify(chargerMapper).deleteById(chargerId);
        verify(stationMapper).update(any(Station.class));
    }

    @Test
    void deleteCharger_withNonExistentId_shouldStillReturn200() {
        UUID nonExistentId = UUID.randomUUID();
        when(chargerMapper.findById(nonExistentId)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = stationController.deleteCharger(nonExistentId);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("删除成功", response.getBody().get("message"));
        verify(chargerMapper).findById(nonExistentId);
        verify(chargerMapper).deleteById(nonExistentId);
        // No station update since charger wasn't found
        verify(stationMapper, never()).findById(any());
    }
}
