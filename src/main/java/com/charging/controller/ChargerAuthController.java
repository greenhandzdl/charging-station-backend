package com.charging.controller;

import com.charging.infrastructure.dto.ChargerLoginRequest;
import com.charging.infrastructure.dto.ChargerLoginResponse;
import com.charging.service.ChargerUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class ChargerAuthController {

    private final ChargerUserService chargerUserService;

    /**
     * 充电桩设备登录（与 Flutter 用户登录完全分离）。
     * 使用独立 JWT（scope=charger），由 ChargerAuthFilter 解析。
     */
    @PostMapping("/charger-login")
    public ResponseEntity<ChargerLoginResponse> chargerLogin(@Valid @RequestBody ChargerLoginRequest request) {
        ChargerLoginResponse response = chargerUserService.login(request);
        return ResponseEntity.ok(response);
    }
}