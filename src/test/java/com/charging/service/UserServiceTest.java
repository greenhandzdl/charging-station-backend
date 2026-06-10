package com.charging.service;

import com.charging.entity.User;
import com.charging.enums.UserRole;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtTokenProvider;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.PasswordHistoryMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private PasswordHistoryMapper passwordHistoryMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private UserService userService;
    private JwtTokenProvider jwtTokenProvider;

    private UUID userId;
    private UUID targetUserId;
    private User testUser;

    /**
     * RedisTemplate subclass that overrides only the methods used by
     * UserServiceImpl, delegating opsForValue() to the mock and
     * making expire/delete no-ops. This avoids Mockito's JDK-25
     * final-class compatibility issue with RedisTemplate.
     */
    static class MockRedisTemplate extends RedisTemplate<String, Object> {

        private final ValueOperations<String, Object> valueOps;

        MockRedisTemplate(ValueOperations<String, Object> valueOps) {
            this.valueOps = valueOps;
        }

        @Override
        public ValueOperations<String, Object> opsForValue() {
            return valueOps;
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public Boolean delete(String key) {
            return true;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();
        setField(jwtTokenProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-32-characters-long!!");
        setField(jwtTokenProvider, "accessTokenExpiration", 900000L);
        setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);
        jwtTokenProvider.init();

        RedisTemplate<String, Object> redisTemplate = new MockRedisTemplate(valueOperations);
        userService = new UserServiceImpl(userMapper, auditLogMapper, redisTemplate,
                jwtTokenProvider, passwordHistoryMapper, passwordEncoder);

        userId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .name("TestUser")
                .phone("13800138000")
                .passwordHash("hashedPassword")
                .role(UserRole.USER)
                .balance(new BigDecimal("100.00"))
                .failedLoginAttempts(0)
                .accountLockedUntil(null)
                .build();
    }

    // ==================== Registration Tests ====================

    @Test
    void register_withNullCaptchaId_shouldThrow() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139000")
                .password("Password1@")
                .captchaId(null)
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139000")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("验证码不能为空"));
    }

    @Test
    void register_withEmptyCaptchaId_shouldThrow() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139001")
                .password("Password1@")
                .captchaId("")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139001")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("验证码不能为空"));
    }

    @Test
    void register_withValidCaptcha_shouldSucceed() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139002")
                .password("Password1@")
                .captchaId("valid-captcha-id")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139002")).thenReturn(1L);
        when(valueOperations.get("captcha:valid-captcha-id")).thenReturn("ABCD");
        when(userMapper.findByPhone("13900139002")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password1@")).thenReturn("encodedPassword");

        userService.register(request, "127.0.0.1");

        verify(userMapper).insert(any(User.class));
        verify(auditLogMapper).insert(any());
    }

    @Test
    void register_shouldThrowTooManyRequests_whenIpRateLimited() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139003")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(4L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("注册过于频繁"));
    }

    @Test
    void register_shouldThrowTooManyRequests_whenPhoneRateLimited() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139004")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139004")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("该手机号今日已注册"));
    }

    @Test
    void register_withWeakPassword_shouldThrow() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139005")
                .password("12345678")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139005")).thenReturn(1L);
        when(valueOperations.get("captcha:captcha-id")).thenReturn("ABCD");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        // "12345678": digit-only (1 category) < 3 required categories
        assertTrue(ex.getMessage().contains("密码"));
    }

    @Test
    void register_withExistingPhone_shouldThrowConflict() {
        RegisterRequest request = RegisterRequest.builder()
                .name("NewUser")
                .phone("13900139006")
                .password("Password1@")
                .captchaId("captcha-id")
                .captchaCode("ABCD")
                .build();

        when(valueOperations.increment("register:ip:127.0.0.1")).thenReturn(1L);
        when(valueOperations.increment("register:phone:13900139006")).thenReturn(1L);
        when(valueOperations.get("captcha:captcha-id")).thenReturn("ABCD");
        when(userMapper.findByPhone("13900139006")).thenReturn(Optional.of(testUser));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("该手机号已注册"));
    }

    // ==================== Login Tests ====================

    @Test
    void login_withWrongPassword_shouldIncrementFailedAttempts() {
        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("wrongPassword")
                .build();

        when(valueOperations.get("login:fail:ip:127.0.0.1")).thenReturn(null);
        when(userMapper.findByPhone("13800138000")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("手机号或密码错误"));

        verify(userMapper).incrementFailedAttempts("13800138000");
        verify(auditLogMapper).insert(argThat(log ->
                "LOGIN_FAILED".equals(log.getAction())));
    }

    @Test
    void login_withLockedAccount_shouldThrow() {
        testUser.setFailedLoginAttempts(10);
        testUser.setAccountLockedUntil(LocalDateTime.now().plusHours(1));

        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("anyPassword")
                .build();

        when(valueOperations.get("login:fail:ip:127.0.0.1")).thenReturn(null);
        when(userMapper.findByPhone("13800138000")).thenReturn(Optional.of(testUser));

        assertThrows(BusinessException.class,
                () -> userService.login(request, "127.0.0.1"));
    }

    @Test
    void login_withCorrectPassword_shouldResetFailedAttempts() {
        testUser.setFailedLoginAttempts(5);

        LoginRequest request = LoginRequest.builder()
                .phone("13800138000")
                .password("correctPassword")
                .build();

        when(valueOperations.get("login:fail:ip:127.0.0.1")).thenReturn(null);
        when(userMapper.findByPhone("13800138000")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

        LoginResponse response = userService.login(request, "127.0.0.1");

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        verify(userMapper).resetFailedAttempts("13800138000");
    }

    // ==================== Role Change Tests ====================

    @Test
    void changeRole_ADMIN_cannotModifySUPER_ADMIN() {
        User targetSuperAdmin = User.builder()
                .id(targetUserId)
                .name("TargetSuperAdmin")
                .role(UserRole.SUPER_ADMIN)
                .build();

        when(userMapper.findById(targetUserId)).thenReturn(Optional.of(targetSuperAdmin));

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("USER")
                .build();

        UUID adminId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(targetUserId, request, adminId, "ADMIN", "127.0.0.1"));
        assertTrue(ex.getMessage().contains("ADMIN不可修改SUPER_ADMIN角色"));
    }

    @Test
    void changeRole_SUPER_ADMIN_cannotModifySelf() {
        User selfUser = User.builder()
                .id(targetUserId)
                .name("SelfUser")
                .role(UserRole.SUPER_ADMIN)
                .build();

        when(userMapper.findById(targetUserId)).thenReturn(Optional.of(selfUser));

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("USER")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(targetUserId, request, targetUserId, "SUPER_ADMIN", "127.0.0.1"));
        assertTrue(ex.getMessage().contains("不可修改自身角色"));
    }

    @Test
    void changeRole_ADMIN_cannotModifyOtherADMIN() {
        User otherAdmin = User.builder()
                .id(targetUserId)
                .name("OtherAdmin")
                .role(UserRole.ADMIN)
                .build();

        when(userMapper.findById(targetUserId)).thenReturn(Optional.of(otherAdmin));

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("USER")
                .build();

        UUID adminId = UUID.randomUUID();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changeRole(targetUserId, request, adminId, "ADMIN", "127.0.0.1"));
        assertTrue(ex.getMessage().contains("ADMIN不可修改其他ADMIN角色"));

        verify(userMapper, never()).update(any());
    }

    @Test
    void changeRole_SUPER_ADMIN_canModifyOtherUsers() {
        User targetUser = User.builder()
                .id(targetUserId)
                .name("TargetUser")
                .role(UserRole.USER)
                .build();

        when(userMapper.findById(targetUserId)).thenReturn(Optional.of(targetUser));

        ChangeRoleRequest request = ChangeRoleRequest.builder()
                .role("ADMIN")
                .build();

        UUID superAdminId = UUID.randomUUID();
        userService.changeRole(targetUserId, request, superAdminId, "SUPER_ADMIN", "127.0.0.1");

        verify(userMapper).update(argThat(u -> u.getRole() == UserRole.ADMIN));
        verify(auditLogMapper).insert(argThat(log ->
                "CHANGE_ROLE".equals(log.getAction())));
    }



    // ==================== searchUsers Tests ====================

    @Test
    void searchUsers_byName_returnsMatches() {
        User match1 = User.builder()
                .id(UUID.randomUUID())
                .name("张三")
                .phone("13800138001")
                .role(UserRole.USER)
                .build();
        User match2 = User.builder()
                .id(UUID.randomUUID())
                .name("张三丰")
                .phone("13800138002")
                .role(UserRole.USER)
                .build();
        User nonMatch = User.builder()
                .id(UUID.randomUUID())
                .name("李四")
                .phone("13800138003")
                .role(UserRole.USER)
                .build();

        when(userMapper.searchByKeyword("张三")).thenReturn(List.of(match1, match2));

        List<User> result = userService.searchUsers("张三");

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> "张三".equals(u.getName())));
        assertTrue(result.stream().anyMatch(u -> "张三丰".equals(u.getName())));
    }

    @Test
    void searchUsers_byPhone_returnsMatches() {
        User found = User.builder()
                .id(UUID.randomUUID())
                .name("TestUser")
                .phone("13800138000")
                .role(UserRole.USER)
                .build();

        when(userMapper.searchByKeyword("13800138000")).thenReturn(List.of(found));

        List<User> result = userService.searchUsers("13800138000");

        assertEquals(1, result.size());
        assertEquals("13800138000", result.get(0).getPhone());
    }

    @Test
    void searchUsers_emptyKeyword_returnsAll() {
        User u1 = User.builder().id(UUID.randomUUID()).name("User1").phone("1").build();
        User u2 = User.builder().id(UUID.randomUUID()).name("User2").phone("2").build();
        when(userMapper.findAll()).thenReturn(List.of(u1, u2));

        List<User> result = userService.searchUsers("");

        assertEquals(2, result.size());
    }

    @Test
    void searchUsers_nullKeyword_returnsAll() {
        User u1 = User.builder().id(UUID.randomUUID()).name("User1").phone("1").build();
        when(userMapper.findAll()).thenReturn(List.of(u1));

        List<User> result = userService.searchUsers(null);

        assertEquals(1, result.size());
    }

    @Test
    void searchUsers_noMatch_returnsEmpty() {
        when(userMapper.searchByKeyword("不存在的关键词")).thenReturn(List.of());

        List<User> result = userService.searchUsers("不存在的关键词");

        assertTrue(result.isEmpty());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}