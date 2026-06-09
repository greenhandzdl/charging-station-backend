package com.charging.service;

import com.charging.infrastructure.dto.CaptchaResult;

public interface CaptchaService {
    CaptchaResult generateCaptcha();
    boolean validateCaptcha(String captchaId, String captchaCode);
}