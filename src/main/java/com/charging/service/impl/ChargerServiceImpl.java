package com.charging.service.impl;

import com.charging.enums.ChargerStatus;
import com.charging.exception.BusinessException;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargerServiceImpl implements ChargerService {

    private final ChargerMapper chargerMapper;

    @Override
    @Transactional
    public void restoreCharger(UUID chargerId) {
        int updated = chargerMapper.updateStatusConditionally(chargerId, "idle", "fault");
        if (updated == 0) {
            log.warn("Charger {} was not in fault status or not found", chargerId);
        }
    }

    @Override
    public ChargerStatus getChargerStatus(UUID chargerId) {
        String status = chargerMapper.findStatusById(chargerId);
        if (status == null) {
            throw BusinessException.notFound("Charger", chargerId.toString());
        }
        return ChargerStatus.valueOf(status.toUpperCase());
    }

    @Override
    @Transactional
    public void updateStatus(UUID chargerId, ChargerStatus status) {
        chargerMapper.updateStatus(chargerId, status);
    }
}