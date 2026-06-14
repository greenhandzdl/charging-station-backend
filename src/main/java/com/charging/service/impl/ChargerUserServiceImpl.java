package com.charging.service.impl;

import com.charging.entity.AuditLog;
import com.charging.entity.ChargerUser;
import com.charging.entity.User;
import com.charging.enums.UserRole;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.ChargerLoginRequest;
import com.charging.infrastructure.dto.ChargerLoginResponse;
import com.charging.infrastructure.security.ChargerTokenProvider;
import com.charging.mapper.AuditLogMapper;
import com.charging.mapper.ChargerUserMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.ChargerUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargerUserServiceImpl implements ChargerUserService {

    private final ChargerUserMapper chargerUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final ChargerTokenProvider chargerTokenProvider;
    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;

    private static final long TOKEN_EXPIRATION_MS = 86400000L; // 24 hours

    @Override
    public ChargerLoginResponse login(ChargerLoginRequest request) {
        ChargerUser user = chargerUserMapper.findByLoginId(request.getLoginId())
                .orElseThrow(() -> BusinessException.unauthorized("充电桩站账号或密码错误"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw BusinessException.forbidden("充电桩站账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("充电桩站账号或密码错误");
        }

        String accessToken = buildToken(user);

        // Update last login
        chargerUserMapper.updateLastLogin(user.getId());

        log.info("ChargerUser login success: loginId={}, permissionLevel={}, tokenVersion={}",
                user.getLoginId(), user.getPermissionLevel(), user.getTokenVersion());

        return buildResponse(user, accessToken);
    }

    @Override
    @Transactional
    public ChargerLoginResponse resetToken(UUID targetUserId, UUID actorId) {
        ChargerUser target = chargerUserMapper.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("ChargerUser", targetUserId.toString()));

        // Try charger_users first, then users table for ADMIN/SUPER_ADMIN
        ChargerUser actor = chargerUserMapper.findById(actorId).orElse(null);

        if (actor != null) {
            // actor is in charger_users table — use existing permission hierarchy
            if (!canManage(actor.getPermissionLevel(), target.getPermissionLevel())) {
                throw BusinessException.forbidden("权限不足：无法重置上级或同级身份的 token");
            }
            if (!"STATION_GLOBAL".equals(actor.getPermissionLevel())
                    && !isInAncestorChain(target, actorId)) {
                throw BusinessException.forbidden("权限不足：只能重置所属下级身份的 token");
            }
        } else {
            // actor not in charger_users — check users table for ADMIN/SUPER_ADMIN
            User adminUser = userMapper.findById(actorId)
                    .orElseThrow(() -> BusinessException.unauthorized("操作者不存在"));

            UserRole role = adminUser.getRole();
            if (role != UserRole.ADMIN && role != UserRole.SUPER_ADMIN) {
                throw BusinessException.forbidden("权限不足：仅管理员及以上可重置充电桩/站 token");
            }

            // Record audit log for admin-initiated token reset
            auditLogMapper.insert(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .actorId(actorId)
                    .actorType(role.name().toLowerCase())
                    .action("RESET_CHARGER_TOKEN")
                    .resource("charger_user")
                    .resourceId(targetUserId)
                    .build());
        }

        // Increment token version (invalidates all existing tokens)
        chargerUserMapper.incrementTokenVersion(targetUserId);

        // Re-read to get new version
        ChargerUser updated = chargerUserMapper.findById(targetUserId)
                .orElseThrow(() -> BusinessException.notFound("ChargerUser", targetUserId.toString()));

        String accessToken = buildToken(updated);

        log.info("Token reset: targetId={}({}), actorId={}, newVersion={}",
                target.getId(), target.getPermissionLevel(),
                actorId, updated.getTokenVersion());

        return buildResponse(updated, accessToken);
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
    public String getPermissionLevel(UUID chargerUserId) {
        return chargerUserMapper.findById(chargerUserId)
                .map(ChargerUser::getPermissionLevel)
                .orElse(null);
    }

    @Override
    public boolean canAccessCharger(UUID chargerUserId, UUID chargerId) {
        ChargerUser user = chargerUserMapper.findById(chargerUserId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return false;
        }

        String level = user.getPermissionLevel();
        if ("STATION_GLOBAL".equals(level)) {
            return true; // can access any charger
        }

        if ("STATION".equals(level)) {
            if (user.getStationId() == null) return false;
            // Check if charger belongs to this station
            // We do this via a query in the controller layer, but here we rely on the
            // stationId claim. The controller can do the extra verification.
            return true; // station-level scope checked at controller level
        }

        // CHARGER level: must match the bound charger
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

    // ===== Private helpers =====

    private String buildToken(ChargerUser user) {
        return chargerTokenProvider.generateToken(
                user.getId().toString(),
                user.getPermissionLevel(),
                user.getChargerId() != null ? user.getChargerId().toString() : null,
                user.getStationId() != null ? user.getStationId().toString() : null,
                user.getTokenVersion() != null ? user.getTokenVersion() : 0,
                TOKEN_EXPIRATION_MS
        );
    }

    private ChargerLoginResponse buildResponse(ChargerUser user, String accessToken) {
        return ChargerLoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(TOKEN_EXPIRATION_MS)
                .tokenVersion(user.getTokenVersion() != null ? user.getTokenVersion() : 0)
                .chargerUser(ChargerLoginResponse.ChargerUserInfo.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .loginId(user.getLoginId())
                        .permissionLevel(user.getPermissionLevel())
                        .chargerId(user.getChargerId() != null ? user.getChargerId().toString() : null)
                        .stationId(user.getStationId() != null ? user.getStationId().toString() : null)
                        .parentId(user.getParentId() != null ? user.getParentId().toString() : null)
                        .build())
                .build();
    }

    /**
     * Check if actorLevel can manage targetLevel.
     * STATION_GLOBAL > STATION > CHARGER
     */
    private boolean canManage(String actorLevel, String targetLevel) {
        if (actorLevel == null || targetLevel == null) return false;
        return switch (actorLevel) {
            case "STATION_GLOBAL" -> !"STATION_GLOBAL".equals(targetLevel); // can't manage same level
            case "STATION" -> "CHARGER".equals(targetLevel);                // can only manage CHARGER
            default -> false;                                                // CHARGER can't manage anyone
        };
    }

    /**
     * Check if actorId is in target's ancestor chain (parent, parent-of-parent, etc.)
     */
    private boolean isInAncestorChain(ChargerUser target, UUID actorId) {
        UUID currentParentId = target.getParentId();
        int maxDepth = 10; // prevent infinite loops
        while (currentParentId != null && maxDepth > 0) {
            if (currentParentId.equals(actorId)) {
                return true;
            }
            ChargerUser parent = chargerUserMapper.findById(currentParentId).orElse(null);
            if (parent == null) break;
            currentParentId = parent.getParentId();
            maxDepth--;
        }
        return false;
    }

    @Override
    public List<ChargerUser> findByStationId(UUID stationId) {
        return chargerUserMapper.findByStationId(stationId);
    }

    @Override
    public List<ChargerUser> findAll() {
        return chargerUserMapper.findAll();
    }
}