package com.charging.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class ChargerTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate charger-scoped JWT.
     * Claims: sub=chargerUserId, chargerId=xxx, identityType=SINGLE|GLOBAL, scope=charger
     */
    public String generateToken(String chargerUserId, String chargerId, String identityType, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(chargerUserId)
                .claim("scope", "charger")
                .claim("identityType", identityType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);

        if (chargerId != null) {
            builder.claim("chargerId", chargerId);
        }

        return builder.compact();
    }

    /**
     * Parse and validate token, return io.jsonwebtoken.Claims
     */
    public io.jsonwebtoken.Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Invalid charger JWT token: {}", e.getMessage());
            return null;
        }
    }

    public String getChargerUserIdFromToken(String token) {
        var claims = validateToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    public String getChargerIdFromToken(String token) {
        var claims = validateToken(token);
        return claims != null ? claims.get("chargerId", String.class) : null;
    }

    public String getIdentityTypeFromToken(String token) {
        var claims = validateToken(token);
        return claims != null ? claims.get("identityType", String.class) : null;
    }
}