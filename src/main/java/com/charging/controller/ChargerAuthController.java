package com.charging.controller;

import com.charging.infrastructure.dto.ChargerLoginRequest;
import com.charging.infrastructure.dto.ChargerLoginResponse;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.ChargerUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class ChargerAuthController {

    private final ChargerUserService chargerUserService;

    /**
     * 充电桩站设备登录（与 Flutter 用户登录完全分离）。
     * 使用 login_id + password 登录，返回 scope=charger 的 JWT。
     */
    @PostMapping("/charger-login")
    public ResponseEntity<ChargerLoginResponse> chargerLogin(@Valid @RequestBody ChargerLoginRequest request) {
        ChargerLoginResponse response = chargerUserService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 重置下级身份的 token（上级调用）。
     * 例如：STATION_GLOBAL 可以重置 STATION 和 CHARGER 的 token；
     * STATION 可以重置其所属 CHARGER 的 token。
     * 重置后旧 token 立即失效（token_version 递增）。
     */
    @PostMapping("/charger-reset-token/{targetUserId}")
    public ResponseEntity<?> resetToken(@PathVariable UUID targetUserId,
                                        @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID actorId = UUID.fromString(principal.getUserId());
        ChargerLoginResponse response = chargerUserService.resetToken(targetUserId, actorId);
        return ResponseEntity.ok(Map.of(
                "message", "token 已重置",
                "newToken", response.getAccessToken(),
                "chargerUser", response.getChargerUser()
        ));
    }
}