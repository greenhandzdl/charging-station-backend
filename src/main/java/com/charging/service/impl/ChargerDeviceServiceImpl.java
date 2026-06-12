package com.charging.service.impl;

import com.charging.entity.ChargerDevice;
import com.charging.mapper.ChargerDeviceMapper;
import com.charging.service.ChargerDeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargerDeviceServiceImpl implements ChargerDeviceService {

    private final ChargerDeviceMapper chargerDeviceMapper;

    @Override
    public Optional<ChargerDevice> findByChargerId(UUID chargerId) {
        return chargerDeviceMapper.findByChargerId(chargerId);
    }

    @Override
    public Optional<ChargerDevice> findByAuthToken(String authToken) {
        return chargerDeviceMapper.findByAuthToken(authToken);
    }

    @Override
    @Transactional
    public ChargerDevice create(ChargerDevice device) {
        chargerDeviceMapper.insert(device);
        return device;
    }

    @Override
    @Transactional
    public void updateLastOnline(UUID id) {
        chargerDeviceMapper.updateLastOnline(id);
    }
}