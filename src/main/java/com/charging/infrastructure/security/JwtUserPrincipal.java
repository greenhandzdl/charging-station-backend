package com.charging.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtUserPrincipal implements Principal {

    private String userId;
    private String role;
    private String scope;
    private String token;

    @Override
    public String getName() {
        return userId;
    }
}