package com.charging.service.impl;

import com.charging.entity.AuditLog;
import com.charging.entity.PasswordHistory;
import com.charging.entity.User;
import com.charging.enums.UserRole;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.*;
import com.charging.infrastructure.security.JwtTokenProvider;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.PasswordHistoryMapper;
import io.jsonwebtoken.Claims;
import com.charging.mapper.UserMapper;
import com.charging.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to convert to JSON", e);
            return "{}";
        }
    }

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String SMS_CODE_PREFIX = "sms_code:";
    private static final String PASSWORD_RESET_TOKEN_PREFIX = "password_reset:token:";
    private static final String LOGIN_FAIL_IP_PREFIX = "login:fail:ip:";
    private static final String LOGIN_FAIL_ACCOUNT_PREFIX = "login:fail:account:";

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        // IP rate limiting check
        String ipKey = LOGIN_FAIL_IP_PREFIX + clientIp;
        Integer ipFailCount = (Integer) redisTemplate.opsForValue().get(ipKey);
        if (ipFailCount != null && ipFailCount >= 10) {
            throw BusinessException.tooManyRequests("IP被封禁30分钟");
        }

        // Captcha check if needed (IP failed >= 5 or account failed >= 3)
        boolean needCaptcha = (ipFailCount != null && ipFailCount >= 5);
        if (!needCaptcha) {
            String accountKey = LOGIN_FAIL_ACCOUNT_PREFIX + request.getPhone();
            Integer accountFailCount = (Integer) redisTemplate.opsForValue().get(accountKey);
            needCaptcha = (accountFailCount != null && accountFailCount >= 3);
        }
        if (needCaptcha) {
            validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());
        }

        // Account-level rate limiting
        String accountKey = LOGIN_FAIL_ACCOUNT_PREFIX + request.getPhone();
        Integer accountFailCount = (Integer) redisTemplate.opsForValue().get(accountKey);
        if (accountFailCount != null && accountFailCount >= 5) {
            throw BusinessException.tooManyRequests("账号登录频繁，请15分钟后再试");
        }

        // Find user
        Optional<User> userOpt = userMapper.findByPhone(request.getPhone());
        if (userOpt.isEmpty()) {
            incrementIpFailCount(ipKey);
            throw BusinessException.unauthorized("手机号或密码错误");
        }

        User user = userOpt.get();

        // Check account lock
        if (user.getAccountLockedUntil() != null && user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {
            throw BusinessException.accountLocked();
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Log failed attempt
            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(user.getId())
                    .actorType("user")
                    .action("LOGIN_FAILED")
                    .resource("user")
                    .resourceId(user.getId())
                    .clientIp(clientIp)
                    .build());

            // Increment failed attempts
            userMapper.incrementFailedAttempts(request.getPhone());
            incrementIpFailCount(ipKey);

            // Check if account should be locked
            User updated = userMapper.findByPhone(request.getPhone()).orElse(user);
            if (updated.getFailedLoginAttempts() >= 10) {
                userMapper.lockAccount(user.getId());
                throw BusinessException.accountLocked();
            }

            throw BusinessException.unauthorized("手机号或密码错误");
        }

        // Success - reset failed attempts
        userMapper.resetFailedAttempts(request.getPhone());
        redisTemplate.delete(ipKey);
        redisTemplate.delete(accountKey);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getRole().name(), null);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        // Store refresh token in Redis
        String refreshKey = REFRESH_TOKEN_PREFIX + user.getId();
        redisTemplate.opsForValue().set(refreshKey, refreshToken,
                jwtTokenProvider.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(user.getId())
                .actorType("user")
                .action("LOGIN_SUCCESS")
                .resource("user")
                .resourceId(user.getId())
                .clientIp(clientIp)
                .build());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .phone(user.getPhone())
                        .role(user.getRole().name())
                        .balance(user.getBalance())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        // IP rate limiting
        String ipKey = "register:ip:" + clientIp;
        Long ipCount = redisTemplate.opsForValue().increment(ipKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipKey, 1, TimeUnit.HOURS);
        }
        if (ipCount != null && ipCount > 3) {
            throw BusinessException.tooManyRequests("注册过于频繁，请稍后再试");
        }

        // Phone rate limiting
        String phoneKey = "register:phone:" + request.getPhone();
        Long phoneCount = redisTemplate.opsForValue().increment(phoneKey);
        if (phoneCount != null && phoneCount == 1) {
            redisTemplate.expire(phoneKey, 24, TimeUnit.HOURS);
        }
        if (phoneCount != null && phoneCount > 1) {
            throw BusinessException.tooManyRequests("该手机号今日已注册");
        }

        // Validate captcha
        validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());

        // Validate password strength (8+ chars, letter + digit)
        validatePasswordStrength(request.getPassword());

        // Check if phone already registered
        if (userMapper.findByPhone(request.getPhone()).isPresent()) {
            throw BusinessException.conflict("该手机号已注册");
        }

        // Create user
        User user = User.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .phone(request.getPhone())
                .plateNumber(request.getPlateNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .balance(BigDecimal.ZERO)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(user.getId())
                .actorType("user")
                .action("REGISTER")
                .resource("user")
                .resourceId(user.getId())
                .clientIp(clientIp)
                .build());
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String oldToken = request.getRefreshToken();
        Claims claims = jwtTokenProvider.validateToken(oldToken);
        if (claims == null) {
            throw BusinessException.unauthorized("Refresh token无效");
        }

        String userId = claims.getSubject();
        String refreshKey = REFRESH_TOKEN_PREFIX + userId;
        String storedToken = (String) redisTemplate.opsForValue().get(refreshKey);

        if (storedToken == null || !storedToken.equals(oldToken)) {
            // Token replay detected
            if (storedToken != null) {
                log.warn("Token replay detected for user: {}", userId);
                auditLogMapper.insert(AuditLog.builder()
                        .id(UUID.randomUUID())
                        .actorId(UUID.fromString(userId))
                        .actorType("user")
                        .action("TOKEN_REPLAY_DETECTED")
                        .resource("user")
                        .resourceId(UUID.fromString(userId))
                        .build());
                // Revoke all sessions
                redisTemplate.delete(refreshKey);
            }
            throw BusinessException.unauthorized("Refresh token已失效");
        }

        // Rotate: delete old, set new
        redisTemplate.delete(refreshKey);
        
        // 从数据库查询用户的实际role，而不是从refresh token中读取（refresh token不包含role）
        String role = userMapper.findById(UUID.fromString(userId))
                .map(user -> user.getRole().name())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
        
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, role, null);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        redisTemplate.opsForValue().set(refreshKey, newRefreshToken,
                jwtTokenProvider.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

        // Include user info for client state sync
        LoginResponse.LoginResponseBuilder builder = LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000);

        userMapper.findById(UUID.fromString(userId)).ifPresent(user ->
                builder.user(LoginResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .phone(user.getPhone())
                        .role(user.getRole().name())
                        .balance(user.getBalance())
                        .build())
        );

        return builder.build();
    }

    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request, String clientIp) {
        // IP rate limiting
        String ipKey = "password_reset:ip:" + clientIp;
        Long ipCount = redisTemplate.opsForValue().increment(ipKey);
        if (ipCount != null && ipCount == 1) {
            redisTemplate.expire(ipKey, 5, TimeUnit.MINUTES);
        }
        if (ipCount != null && ipCount > 3) {
            throw BusinessException.tooManyRequests("请求过于频繁，请稍后再试");
        }

        // Phone rate limiting
        String phoneKey = "password_reset:phone:" + request.getPhone();
        Long phoneCount = redisTemplate.opsForValue().increment(phoneKey);
        if (phoneCount != null && phoneCount == 1) {
            redisTemplate.expire(phoneKey, 24, TimeUnit.HOURS);
        }
        if (phoneCount != null && phoneCount > 3) {
            throw BusinessException.tooManyRequests("该手机号今日重置次数过多");
        }

        // Validate captcha
        validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());

        // Find user
        Optional<User> userOpt = userMapper.findByPhone(request.getPhone());
        if (userOpt.isEmpty()) {
            throw BusinessException.notFound("User", request.getPhone());
        }

        User user = userOpt.get();

        // Generate SMS code (mock - in production, send actual SMS)
        String smsCode = String.format("%06d", new Random().nextInt(1000000));
        redisTemplate.opsForValue().set(SMS_CODE_PREFIX + request.getPhone(), smsCode, 5, TimeUnit.MINUTES);
        log.info("SMS code for {}: {}", request.getPhone(), smsCode);

        // Generate password reset token
        String resetToken = UUID.randomUUID().toString();
        String tokenHash = passwordEncoder.encode(resetToken);
        String tokenPlainHash = sha256(resetToken);

        userMapper.setPasswordResetToken(user.getId(), tokenHash, tokenPlainHash);

        // Store in Redis
        redisTemplate.opsForValue().set(
                PASSWORD_RESET_TOKEN_PREFIX + resetToken, user.getId().toString(),
                15, TimeUnit.MINUTES);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(user.getId())
                .actorType("user")
                .action("PASSWORD_RESET_REQUEST")
                .resource("user")
                .resourceId(user.getId())
                .clientIp(clientIp)
                .build());
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request, String clientIp) {
        // Validate captcha
        validateCaptcha(request.getCaptchaId(), request.getCaptchaCode());

        // Validate SMS code
        String smsKey = SMS_CODE_PREFIX + extractPhoneFromToken(request.getToken());
        String storedSmsCode = (String) redisTemplate.opsForValue().get(smsKey);
        if (storedSmsCode == null || !storedSmsCode.equals(request.getSmsCode())) {
            throw BusinessException.badRequest("短信验证码错误或已过期");
        }
        redisTemplate.delete(smsKey);

        // Validate reset token from Redis
        String redisKey = PASSWORD_RESET_TOKEN_PREFIX + request.getToken();
        String userIdStr = (String) redisTemplate.opsForValue().get(redisKey);
        // Also try to look up by token hash from DB
        String tokenPlainHash = sha256(request.getToken());
        Optional<User> userOpt = userMapper.findByPasswordResetTokenHash(tokenPlainHash);

        if (userOpt.isEmpty() || userIdStr == null) {
            throw BusinessException.badRequest("重置令牌无效或已过期");
        }

        User user = userOpt.get();

        // Validate password strength
        validatePasswordStrength(request.getNewPassword());
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());

        // Check password history (reject if matches recent 3)
        checkPasswordHistory(user.getId(), newPasswordHash);

        userMapper.updatePassword(user.getId(), newPasswordHash);

        // Save new password hash to history and prune old records
        passwordHistoryMapper.insert(PasswordHistory.builder()
                .userId(user.getId())
                .passwordHash(newPasswordHash)
                .build());
        passwordHistoryMapper.deleteOldRecords(user.getId());

        // Clean up
        redisTemplate.delete(redisKey);
        redisTemplate.delete(smsKey);
        userMapper.clearPasswordResetToken(user.getId());

        // Revoke all refresh tokens
        String pattern = REFRESH_TOKEN_PREFIX + user.getId() + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null) {
            redisTemplate.delete(keys);
        }

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(user.getId())
                .actorType("user")
                .action("PASSWORD_RESET_CONFIRM")
                .resource("user")
                .resourceId(user.getId())
                .payload("{\"source\": \"self_reset\"}")
                .clientIp(clientIp)
                .build());
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String clientIp) {
        User user = userMapper.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("旧密码错误");
        }

        validatePasswordStrength(request.getNewPassword());
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());

        // Check password history (reject if matches recent 3)
        checkPasswordHistory(user.getId(), newPasswordHash);

        userMapper.updatePassword(userId, newPasswordHash);

        // Save new password hash to history and prune old records
        passwordHistoryMapper.insert(PasswordHistory.builder()
                .userId(userId)
                .passwordHash(newPasswordHash)
                .build());
        passwordHistoryMapper.deleteOldRecords(userId);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType("user")
                .action("CHANGE_PASSWORD")
                .resource("user")
                .resourceId(userId)
                .clientIp(clientIp)
                .build());
    }

    @Override
    public List<User> listUsers(Map<String, String> params) {
        return userMapper.findAll();
    }

    @Override
    @Transactional
    public User updateUser(UUID id, UpdateUserRequest request, UUID currentUserId, String currentUserRole) {
        User target = userMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User", id.toString()));
        User current = userMapper.findById(currentUserId)
                .orElseThrow(() -> BusinessException.notFound("User", currentUserId.toString()));

        // ADMIN cannot modify other ADMIN or SUPER_ADMIN (self-editing allowed)
        if (currentUserRole.equals("ADMIN") && !currentUserId.equals(id)) {
            UserRole targetRole = target.getRole();
            if (targetRole == UserRole.ADMIN || targetRole == UserRole.SUPER_ADMIN) {
                throw BusinessException.forbidden("ADMIN不可修改其他ADMIN或SUPER_ADMIN");
            }
        }

        // SUPER_ADMIN cannot modify other SUPER_ADMIN (self-editing allowed)
        if (currentUserRole.equals("SUPER_ADMIN") && !currentUserId.equals(id)) {
            if (target.getRole() == UserRole.SUPER_ADMIN) {
                throw BusinessException.forbidden("SUPER_ADMIN不可修改其他SUPER_ADMIN");
            }
        }

        target.setName(request.getName() != null ? request.getName() : target.getName());
        target.setPlateNumber(request.getPlateNumber() != null ? request.getPlateNumber() : target.getPlateNumber());
        target.setUpdatedAt(LocalDateTime.now());

        userMapper.update(target);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(currentUserId)
                .actorType("admin")
                .action("UPDATE_USER")
                .resource("user")
                .resourceId(id)
                .clientIp(null)
                .build());

        return target;
    }

    @Override
    @Transactional
    public User updateProfile(UUID userId, UpdateUserRequest request) {
        log.debug("updateProfile: userId={}, name={}, plateNumber={}", userId, request.getName(), request.getPlateNumber());
        User user = userMapper.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User", userId.toString()));
        log.debug("updateProfile: found user id={}, role={}, phone={}", user.getId(), user.getRole(), user.getPhone());

        user.setName(request.getName() != null ? request.getName() : user.getName());
        user.setPlateNumber(request.getPlateNumber() != null ? request.getPlateNumber() : user.getPlateNumber());
        user.setUpdatedAt(LocalDateTime.now());

        int updated = userMapper.update(user);
        log.debug("updateProfile: userMapper.update returned {}", updated);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(userId)
                .actorType(user.getRole() != null ? user.getRole().name().toLowerCase() : "user")
                .action("UPDATE_PROFILE")
                .resource("user")
                .resourceId(userId)
                .payload(toJson(Map.of("name", user.getName() != null ? user.getName() : "", "plateNumber", user.getPlateNumber() != null ? user.getPlateNumber() : "")))
                .build());

        log.debug("updateProfile: returning user id={}, name={}, plateNumber={}", user.getId(), user.getName(), user.getPlateNumber());
        return user;
    }

    @Override
    @Transactional
    public void deleteUser(UUID id, UUID currentUserId, String currentUserRole) {
        User target = userMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User", id.toString()));

        // ADMIN cannot delete other ADMIN or SUPER_ADMIN
        if (currentUserRole.equals("ADMIN")) {
            UserRole targetRole = target.getRole();
            if (targetRole == UserRole.ADMIN || targetRole == UserRole.SUPER_ADMIN) {
                throw BusinessException.forbidden("ADMIN不可删除其他ADMIN或SUPER_ADMIN");
            }
        }

        // SUPER_ADMIN cannot delete self
        if (currentUserRole.equals("SUPER_ADMIN") && currentUserId.equals(id)) {
            throw BusinessException.forbidden("SUPER_ADMIN不可删除自身");
        }

        userMapper.deleteById(id);

        // Audit log
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(currentUserId)
                .actorType("admin")
                .action("DELETE_USER")
                .resource("user")
                .resourceId(id)
                .build());
    }

    @Override
    public BigDecimal getBalance(UUID userId) {
        return userMapper.findBalanceById(userId);
    }

    @Override
    @Transactional
    public void changeRole(UUID id, ChangeRoleRequest request, UUID currentUserId, String currentUserRole, String clientIp) {
        User target = userMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User", id.toString()));

        // Prevent self-role-change
        if (currentUserId.equals(id)) {
            throw BusinessException.forbidden("不可修改自身角色");
        }

        // ADMIN cannot promote self or modify other ADMIN or SUPER_ADMIN
        if (currentUserRole.equals("ADMIN")) {
            if (target.getRole() == UserRole.ADMIN) {
                throw BusinessException.forbidden("ADMIN不可修改其他ADMIN角色");
            }
            if (target.getRole() == UserRole.SUPER_ADMIN) {
                throw BusinessException.forbidden("ADMIN不可修改SUPER_ADMIN角色");
            }
        }

        UserRole newRole;
        try {
            newRole = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无效的角色类型");
        }

        UserRole oldRole = target.getRole();
        target.setRole(newRole);
        target.setUpdatedAt(LocalDateTime.now());
        userMapper.update(target);

        // Audit log with before/after roles
        auditLogMapper.insert(AuditLog.builder()
                .id(UUID.randomUUID())
                .actorId(currentUserId)
                .actorType("admin")
                .action("CHANGE_ROLE")
                .resource("user")
                .resourceId(id)
                .payload("{\"old_role\": \"" + oldRole.name().toLowerCase()
                        + "\", \"new_role\": \"" + newRole.name().toLowerCase() + "\"}")
                .clientIp(clientIp)
                .build());
    }

    @Override
    public List<User> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return userMapper.findAll();
        }
        return userMapper.searchByKeyword(keyword.trim());
    }

    @Override
    public User findById(UUID id) {
        return userMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("User", id.toString()));
    }

    // ========== Private helpers ==========

    private void validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null || captchaId.isBlank() || captchaCode.isBlank()) {
            throw BusinessException.badRequest("验证码不能为空");
        }
        // 开发环境 mock 验证码：captchaId=mock 时跳过
        if ("mock".equals(captchaId)) {
            log.debug("Dev mock captcha: skipping Redis check");
            return;
        }
        String key = CAPTCHA_PREFIX + captchaId;
        String stored;
        try {
            stored = (String) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis unavailable for captcha validation, skipping: {}", e.getMessage());
            return;
        }
        if (stored == null || !stored.equalsIgnoreCase(captchaCode)) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(key);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw BusinessException.badRequest("密码长度至少8位");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> SPECIAL_CHARS.indexOf(ch) >= 0);

        int categories = 0;
        if (hasUpper) categories++;
        if (hasLower) categories++;
        if (hasDigit) categories++;
        if (hasSpecial) categories++;

        if (categories < 3) {
            throw BusinessException.badRequest("密码必须包含大写字母、小写字母、数字、特殊字符中的至少3类");
        }
    }

    private static final String SPECIAL_CHARS = "@$!%*#?&.";

    private void incrementIpFailCount(String ipKey) {
        Long count = redisTemplate.opsForValue().increment(ipKey);
        if (count != null && count == 1) {
            redisTemplate.expire(ipKey, 5, TimeUnit.MINUTES);
        }
    }

    private void checkPasswordHistory(UUID userId, String newPasswordHash) {
        List<PasswordHistory> recent = passwordHistoryMapper.findRecentByUserId(userId);
        // Also include the current password hash in the check
        User cur = userMapper.findById(userId).orElse(null);
        if (cur != null) {
            if (cur.getPasswordHash().equals(newPasswordHash)) {
                throw BusinessException.badRequest("新密码不能与当前密码相同");
            }
            for (PasswordHistory h : recent) {
                if (h.getPasswordHash().equals(newPasswordHash)) {
                    throw BusinessException.badRequest("新密码不能与最近3次使用的密码相同");
                }
            }
        }
    }

    private String extractPhoneFromToken(String token) {
        // In a real implementation, extract from Redis mapping
        return ""; // stub
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}