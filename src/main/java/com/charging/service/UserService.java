package com.charging.service;

import com.charging.infrastructure.dto.*;
import com.charging.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UserService {
    LoginResponse login(LoginRequest request, String clientIp);
    void register(RegisterRequest request, String clientIp);
    LoginResponse refreshToken(RefreshTokenRequest request);
    void resetPassword(PasswordResetRequest request, String clientIp);
    void confirmPasswordReset(PasswordResetConfirmRequest request, String clientIp);
    void changePassword(UUID userId, ChangePasswordRequest request, String clientIp);
    List<User> listUsers(Map<String, String> params);
    User updateUser(UUID id, UpdateUserRequest request, UUID currentUserId, String currentUserRole);
    void deleteUser(UUID id, UUID currentUserId, String currentUserRole);
    BigDecimal getBalance(UUID userId);
    void changeRole(UUID id, ChangeRoleRequest request, UUID currentUserId, String currentUserRole, String clientIp);
    User findById(UUID id);
}