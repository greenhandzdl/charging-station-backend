package com.charging.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargerLoginResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private int tokenVersion;
    private ChargerUserInfo chargerUser;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargerUserInfo {
        private String id;
        private String name;
        private String loginId;
        private String permissionLevel;
        private String chargerId;
        private String stationId;
        private String parentId;
    }
}