package com.charging.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmRequest {

    @NotBlank(message = "重置令牌不能为空")
    private String token;

    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "密码长度至少8位")
    private String newPassword;

    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}