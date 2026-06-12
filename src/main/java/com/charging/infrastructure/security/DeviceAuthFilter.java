package com.charging.infrastructure.security;

import com.charging.entity.ChargerDevice;
import com.charging.mapper.ChargerDeviceMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 充电桩设备认证过滤器。
 * 当请求头 X-Device-Token 匹配 charger_devices 表中的 auth_token 时，
 * 授予设备权限（SCOPE_device），用于插枪/拔枪等设备操作。
 * Swing 模拟充电桩通过此令牌认证，无需 JWT 登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class DeviceAuthFilter extends OncePerRequestFilter {

    private final ChargerDeviceMapper chargerDeviceMapper;

    private static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String deviceToken = request.getHeader(DEVICE_TOKEN_HEADER);
        if (deviceToken != null && !deviceToken.isEmpty()) {
            Optional<ChargerDevice> deviceOpt = chargerDeviceMapper.findByAuthToken(deviceToken);
            if (deviceOpt.isPresent()) {
                ChargerDevice device = deviceOpt.get();
                var authorities = List.of(
                        new SimpleGrantedAuthority("SCOPE_device"),
                        new SimpleGrantedAuthority("ROLE_DEVICE")
                );
                var principal = new JwtUserPrincipal(
                        device.getChargerId().toString(),
                        "DEVICE",
                        "device",
                        null
                );
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Device auth successful: chargerId={}, deviceName={}", device.getChargerId(), device.getDeviceName());
            } else {
                log.warn("Invalid device token provided");
            }
        }
        filterChain.doFilter(request, response);
    }
}