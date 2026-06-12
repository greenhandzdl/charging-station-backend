package com.charging.service.impl;

import com.charging.entity.ChargerUser;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.ChargerLoginRequest;
import com.charging.infrastructure.dto.ChargerLoginResponse;
import com.charging.infrastructure.security.ChargerTokenProvider;
import com.charging.mapper.ChargerUserMapper;
import com.charging.service.ChargerUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargerUserServiceImpl implements ChargerUserService {

    private final ChargerUserMapper chargerUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final ChargerTokenProvider chargerTokenProvider;

    @Override
    public ChargerLoginResponse login(ChargerLoginRequest request) {
        ChargerUser user = chargerUserMapper.findByPhone(request.getPhone())
                .orElseThrow(() -> BusinessException.unauthorized("充电桩账号或密码错误"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw BusinessException.forbidden("充电桩账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("充电桩账号或密码错误");
        }

        // Generate charger-scoped JWT
        long expiresIn = 86400000L; // 24 hours
        String accessToken = chargerTokenProvider.generateToken(
                user.getId().toString(),
                user.getChargerId() != null ? user.getChargerId().toString() : null,
                user.getIdentityType(),
                expiresIn
        );

        // Update last login
        chargerUserMapper.updateLastLogin(user.getId());

        log.info("Charger login success: phone={}, identityType={}", user.getPhone(), user.getIdentityType());

        return ChargerLoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .chargerUser(ChargerLoginResponse.ChargerUserInfo.builder()
                        .id(user.getId().toString())
                        .chargerId(user.getChargerId() != null ? user.getChargerId().toString() : null)
                        .name(user.getName())
                        .phone(user.getPhone())
                        .identityType(user.getIdentityType())
                        .build())
                .build();
    }

    @Override
    public Optional<ChargerUser> findById(UUID id) {
        return chargerUserMapper.findById(id);
    }

    @Override
    public Optional<ChargerUser> findByChargerId(UUID chargerId) {
        return chargerUserMapper.findByChargerId(chargerId);
    }

    @Override
    public boolean canAccessCharger(UUID chargerUserId, UUID chargerId) {
        ChargerUser user = chargerUserMapper.findById(chargerUserId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return false;
        }
        // GLOBAL identity can access any charger
        if ("GLOBAL".equals(user.getIdentityType())) {
            String allowedIds = user.getAllowedChargerIds();
            if (allowedIds == null || allowedIds.isBlank()) {
                return true; // empty = all chargers
            }
            // Check if target charger is in allowed list (JSON array string)
            String targetStr = chargerId.toString();
            return allowedIds.contains(targetStr);
        }
        // SINGLE identity: must match the bound charger
        return chargerId.equals(user.getChargerId());
    }

    @Override
    public void updateLastLogin(UUID id) {
        chargerUserMapper.updateLastLogin(id);
    }

    @Override
    @Transactional
    public ChargerUser create(ChargerUser chargerUser) {
        chargerUserMapper.insert(chargerUser);
        return chargerUser;
    }
}