package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargerLoginRequest {

    @NotBlank(message = "登录账号不能为空")
    private String loginId;

    @NotBlank(message = "密码不能为空")
    private String password;
}