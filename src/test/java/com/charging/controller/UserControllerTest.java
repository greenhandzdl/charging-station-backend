package com.charging.controller;

import com.charging.entity.User;
import com.charging.enums.UserRole;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest httpServletRequest;

    private UserController userController;

    private UUID userId;
    private UUID targetUserId;
    private JwtUserPrincipal userPrincipal;
    private JwtUserPrincipal adminPrincipal;
    private User testUser;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService);

        userId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        userPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();

        adminPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("ADMIN")
                .build();

        testUser = User.builder()
                .id(targetUserId)
                .name("TestUser")
                .phone("13800138000")
                .role(UserRole.USER)
                .balance(new BigDecimal("100.00"))
                .build();
    }

    private void mockClientIp(String ip) {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn(ip);
    }

    // ==================== register Tests ====================

    @Test
    void register_withValidRequest_shouldReturn201() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139000")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        mockClientIp("192.168.1.1");
        doNothing().when(userService).register(request, "192.168.1.1");

        ResponseEntity<Map<String, String>> response = userController.register(request, httpServletRequest);

        assertEquals(201, response.getStatusCodeValue());
        assertEquals("注册成功", response.getBody().get("message"));
        verify(userService).register(request, "192.168.1.1");
    }

    @Test
    void register_withDuplicatePhone_shouldThrowBusinessException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139000")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        mockClientIp("192.168.1.1");
        doThrow(BusinessException.conflict("该手机号已注册"))
                .when(userService).register(request, "192.168.1.1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.register(request, httpServletRequest));
        assertEquals("CONFLICT", ex.getCode());
        assertTrue(ex.getMessage().contains("该手机号已注册"));
        verify(userService).register(request, "192.168.1.1");
    }

    @Test
    void register_withRateLimit_shouldThrowBusinessException() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139000")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        mockClientIp("192.168.1.1");
        doThrow(BusinessException.tooManyRequests("注册过于频繁"))
                .when(userService).register(request, "192.168.1.1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.register(request, httpServletRequest));
        assertEquals("TOO_MANY_REQUESTS", ex.getCode());
        verify(userService).register(request, "192.168.1.1");
    }

    // ==================== login Tests ====================

    @Test
    void login_withValidCredentials_shouldReturn200() {
        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("correctPassword")
                .build();
        LoginResponse expectedResponse = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900000)
                .user(LoginResponse.UserInfo.builder()
                        .id(userId.toString())
                        .name("TestUser")
                        .phone("13800138000")
                        .role("USER")
                        .balance(new BigDecimal("100.00"))
                        .build())
                .build();

        mockClientIp("192.168.1.2");
        when(userService.login(request, "192.168.1.2")).thenReturn(expectedResponse);

        ResponseEntity<LoginResponse> response = userController.login(request, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().getAccessToken());
        assertEquals("TestUser", response.getBody().getUser().getName());
        verify(userService).login(request, "192.168.1.2");
    }

    @Test
    void login_withWrongPassword_shouldThrowBusinessException() {
        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("wrongPassword")
                .build();

        mockClientIp("192.168.1.2");
        when(userService.login(request, "192.168.1.2"))
                .thenThrow(BusinessException.unauthorized("手机号或密码错误"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.login(request, httpServletRequest));
        assertEquals("UNAUTHORIZED", ex.getCode());
        verify(userService).login(request, "192.168.1.2");
    }

    @Test
    void login_withLockedAccount_shouldThrowBusinessException() {
        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("anyPassword")
                .build();

        mockClientIp("192.168.1.2");
        when(userService.login(request, "192.168.1.2"))
                .thenThrow(BusinessException.accountLocked());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.login(request, httpServletRequest));
        assertEquals("ACCOUNT_LOCKED", ex.getCode());
        verify(userService).login(request, "192.168.1.2");
    }

    // ==================== refreshToken Tests ====================

    @Test
    void refreshToken_withValidRequest_shouldReturn200() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();
        LoginResponse expectedResponse = LoginResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .build();

        when(userService.refreshToken(request)).thenReturn(expectedResponse);

        ResponseEntity<LoginResponse> response = userController.refreshToken(request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("new-access-token", response.getBody().getAccessToken());
        verify(userService).refreshToken(request);
    }

    // ==================== resetPassword Tests ====================

    @Test
    void resetPassword_withValidRequest_shouldReturn200() {
        PasswordResetRequest request = PasswordResetRequest.builder()
                .phone("13800138000")
                .build();

        mockClientIp("192.168.1.3");
        doNothing().when(userService).resetPassword(request, "192.168.1.3");

        ResponseEntity<Map<String, Object>> response = userController.resetPassword(request, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("重置验证码已发送", response.getBody().get("message"));
        assertEquals(900, response.getBody().get("tokenExpireIn"));
        verify(userService).resetPassword(request, "192.168.1.3");
    }

    // ==================== confirmPasswordReset Tests ====================

    @Test
    void confirmPasswordReset_withValidRequest_shouldReturn200() {
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token("reset-token-123")
                .smsCode("123456")
                .newPassword("NewPass1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        mockClientIp("192.168.1.3");
        doNothing().when(userService).confirmPasswordReset(request, "192.168.1.3");

        ResponseEntity<Map<String, String>> response = userController.confirmPasswordReset(request, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("密码重置成功，请重新登录", response.getBody().get("message"));
        verify(userService).confirmPasswordReset(request, "192.168.1.3");
    }

    // ==================== changePassword Tests ====================

    @Test
    void changePassword_withValidRequest_shouldReturn200() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("oldPass1@")
                .newPassword("newPass1@")
                .build();

        mockClientIp("192.168.1.4");
        doNothing().when(userService).changePassword(userId, request, "192.168.1.4");

        ResponseEntity<Map<String, String>> response = userController.changePassword(
                request, userPrincipal, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("密码修改成功", response.getBody().get("message"));
        verify(userService).changePassword(userId, request, "192.168.1.4");
    }

    @Test
    void changePassword_withWrongOldPassword_shouldThrowBusinessException() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .oldPassword("wrongOldPass1@")
                .newPassword("newPass1@")
                .build();

        mockClientIp("192.168.1.4");
        doThrow(BusinessException.forbidden("原密码错误"))
                .when(userService).changePassword(userId, request, "192.168.1.4");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.changePassword(request, userPrincipal, httpServletRequest));
        assertEquals("FORBIDDEN", ex.getCode());
        verify(userService).changePassword(userId, request, "192.168.1.4");
    }

    // ==================== updateUser Tests ====================

    @Test
    void updateUser_withValidRequest_shouldReturn200() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .name("UpdatedName")
                .plateNumber("京A12345")
                .build();
        User updatedUser = User.builder()
                .id(targetUserId)
                .name("UpdatedName")
                .phone("13800138000")
                .plateNumber("京A12345")
                .role(UserRole.USER)
                .build();

        when(userService.updateUser(targetUserId, request, userId, "ADMIN"))
                .thenReturn(updatedUser);

        ResponseEntity<User> response = userController.updateUser(targetUserId, request, adminPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("UpdatedName", response.getBody().getName());
        assertEquals("京A12345", response.getBody().getPlateNumber());
        verify(userService).updateUser(targetUserId, request, userId, "ADMIN");
    }

    @Test
    void updateUser_withNonExistentUser_shouldThrowBusinessException() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .name("UpdatedName")
                .build();

        when(userService.updateUser(targetUserId, request, userId, "ADMIN"))
                .thenThrow(BusinessException.notFound("User", targetUserId.toString()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.updateUser(targetUserId, request, adminPrincipal));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(userService).updateUser(targetUserId, request, userId, "ADMIN");
    }

    // ==================== changeRole Tests ====================

    @Test
    void changeRole_withValidRequest_shouldReturn200() {
        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("ADMIN")
                .build();

        mockClientIp("192.168.1.5");
        doNothing().when(userService).changeRole(targetUserId, request, userId, "ADMIN", "192.168.1.5");

        ResponseEntity<Map<String, String>> response = userController.changeRole(
                targetUserId, request, adminPrincipal, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("角色变更成功", response.getBody().get("message"));
        verify(userService).changeRole(targetUserId, request, userId, "ADMIN", "192.168.1.5");
    }

    @Test
    void changeRole_whenTargetIsSuperAdmin_shouldThrowBusinessException() {
        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("USER")
                .build();

        mockClientIp("192.168.1.5");
        doThrow(BusinessException.forbidden("ADMIN不可修改SUPER_ADMIN角色"))
                .when(userService).changeRole(targetUserId, request, userId, "ADMIN", "192.168.1.5");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.changeRole(targetUserId, request, adminPrincipal, httpServletRequest));
        assertEquals("FORBIDDEN", ex.getCode());
        verify(userService).changeRole(targetUserId, request, userId, "ADMIN", "192.168.1.5");
    }

    // ==================== deleteUser Tests ====================

    @Test
    void deleteUser_withValidId_shouldReturn200() {
        doNothing().when(userService).deleteUser(targetUserId, userId, "ADMIN");

        ResponseEntity<Map<String, String>> response = userController.deleteUser(targetUserId, adminPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("删除成功", response.getBody().get("message"));
        verify(userService).deleteUser(targetUserId, userId, "ADMIN");
    }

    @Test
    void deleteUser_cannotDeleteSelf_shouldThrowBusinessException() {
        doThrow(BusinessException.forbidden("不能删除自身账号"))
                .when(userService).deleteUser(userId, userId, "ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.deleteUser(userId, adminPrincipal));
        assertEquals("FORBIDDEN", ex.getCode());
        verify(userService).deleteUser(userId, userId, "ADMIN");
    }

    // ==================== getBalance Tests ====================

    @Test
    void getBalance_shouldReturn200() {
        when(userService.getBalance(userId)).thenReturn(new BigDecimal("100.00"));

        ResponseEntity<Map<String, BigDecimal>> response = userController.getBalance(userPrincipal);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(new BigDecimal("100.00"), response.getBody().get("balance"));
        verify(userService).getBalance(userId);
    }

    @Test
    void getBalance_forFrozenAccount_shouldThrowBusinessException() {
        when(userService.getBalance(userId))
                .thenThrow(BusinessException.accountFrozen());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userController.getBalance(userPrincipal));
        assertEquals("ACCOUNT_FROZEN", ex.getCode());
        verify(userService).getBalance(userId);
    }

    // ==================== searchUsers Tests ====================

    @Test
    void searchUsers_withKeyword_shouldReturn200WithList() {
        List<User> expectedUsers = List.of(
                User.builder().id(UUID.randomUUID()).name("张三").phone("13800138001").build(),
                User.builder().id(UUID.randomUUID()).name("张三丰").phone("13800138002").build()
        );

        when(userService.searchUsers("张三")).thenReturn(expectedUsers);

        ResponseEntity<List<User>> response = userController.searchUsers("张三");

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        verify(userService).searchUsers("张三");
    }

    @Test
    void searchUsers_withNoMatch_shouldReturnEmptyList() {
        when(userService.searchUsers("不存在的用户")).thenReturn(List.of());

        ResponseEntity<List<User>> response = userController.searchUsers("不存在的用户");

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isEmpty());
        verify(userService).searchUsers("不存在的用户");
    }

    // ==================== listUsers Tests ====================

    @Test
    void listUsers_shouldReturn200WithList() {
        Map<String, String> params = Map.of("page", "1", "size", "20");
        List<User> expectedUsers = List.of(
                User.builder().id(UUID.randomUUID()).name("User1").phone("1").build(),
                User.builder().id(UUID.randomUUID()).name("User2").phone("2").build()
        );

        when(userService.listUsers(params)).thenReturn(expectedUsers);

        ResponseEntity<List<User>> response = userController.listUsers(params);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        verify(userService).listUsers(params);
    }

    @Test
    void listUsers_withEmptyParams_shouldReturn200() {
        Map<String, String> params = Map.of();
        List<User> expectedUsers = List.of();

        when(userService.listUsers(params)).thenReturn(expectedUsers);

        ResponseEntity<List<User>> response = userController.listUsers(params);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isEmpty());
        verify(userService).listUsers(params);
    }
}
