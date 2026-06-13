package com.charging.controller;

import com.charging.entity.User;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request,
                                                        HttpServletRequest httpRequest) {
        userService.register(request, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "注册成功"));
    }

    @PostMapping("/auth/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        LoginResponse response = userService.login(request, getClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/refresh")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = userService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/password-reset")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody PasswordResetRequest request,
                                                              HttpServletRequest httpRequest) {
        userService.resetPassword(request, getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "重置验证码已发送", "tokenExpireIn", 900));
    }

    @PostMapping("/auth/password-reset/confirm")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest httpRequest) {
        userService.confirmPasswordReset(request, getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "密码重置成功，请重新登录"));
    }

    @PutMapping("/auth/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal JwtUserPrincipal principal,
            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(principal.getUserId());
        userService.changePassword(userId, request, getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "密码修改成功"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<User>> listUsers(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(userService.listUsers(params));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateUserRequest request,
                                           @AuthenticationPrincipal JwtUserPrincipal principal) {
        User updated = userService.updateUser(id, request,
                UUID.fromString(principal.getUserId()), principal.getRole());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable UUID id,
                                                          @AuthenticationPrincipal JwtUserPrincipal principal) {
        userService.deleteUser(id, UUID.fromString(principal.getUserId()), principal.getRole());
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/users/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String keyword) {
        return ResponseEntity.ok(userService.searchUsers(keyword));
    }

    @GetMapping("/users/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, BigDecimal>> getBalance(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        BigDecimal balance = userService.getBalance(userId);
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @PutMapping("/users/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateUserRequest request,
                                                              @AuthenticationPrincipal JwtUserPrincipal principal) {
        UUID userId = UUID.fromString(principal.getUserId());
        User updated = userService.updateProfile(userId, request);
        Map<String, Object> result = new HashMap<>();
        result.put("id", updated.getId().toString());
        result.put("name", updated.getName());
        result.put("phone", updated.getPhone());
        result.put("plateNumber", updated.getPlateNumber());
        result.put("role", updated.getRole().name());
        result.put("balance", updated.getBalance());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> changeRole(@PathVariable UUID id,
                                                          @Valid @RequestBody ChangeRoleRequest request,
                                                          @AuthenticationPrincipal JwtUserPrincipal principal,
                                                          HttpServletRequest httpRequest) {
        userService.changeRole(id, request,
                UUID.fromString(principal.getUserId()), principal.getRole(),
                getClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("message", "角色变更成功"));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}