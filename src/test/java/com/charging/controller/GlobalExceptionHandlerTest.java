package com.charging.controller;

import com.charging.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    // ==================== BusinessException ====================

    @Test
    void handleBusinessException_withBadRequest_shouldReturn400() {
        BusinessException ex = BusinessException.badRequest("参数错误");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("BAD_REQUEST", error.get("code"));
        assertEquals("参数错误", error.get("message"));
    }

    @Test
    void handleBusinessException_withUnauthorized_shouldReturn401() {
        BusinessException ex = BusinessException.unauthorized("未授权访问");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("UNAUTHORIZED", error.get("code"));
        assertEquals("未授权访问", error.get("message"));
    }

    @Test
    void handleBusinessException_withForbidden_shouldReturn403() {
        BusinessException ex = BusinessException.forbidden("权限不足");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("FORBIDDEN", error.get("code"));
        assertEquals("权限不足", error.get("message"));
    }

    @Test
    void handleBusinessException_withNotFound_shouldReturn404() {
        BusinessException ex = BusinessException.notFound("Charger", "C001");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("NOT_FOUND", error.get("code"));
        assertTrue(((String) error.get("message")).contains("Charger not found"));
    }

    @Test
    void handleBusinessException_withConflict_shouldReturn409() {
        BusinessException ex = BusinessException.conflict("资源冲突");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("CONFLICT", error.get("code"));
        assertEquals("资源冲突", error.get("message"));
    }

    @Test
    void handleBusinessException_withTooManyRequests_shouldReturn429() {
        BusinessException ex = BusinessException.tooManyRequests("请求过于频繁");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("TOO_MANY_REQUESTS", error.get("code"));
        assertEquals("请求过于频繁", error.get("message"));
    }

    @Test
    void handleBusinessException_withDetails_shouldIncludeDetails() {
        BusinessException ex = new BusinessException("INSUFFICIENT_BALANCE", "余额不足",
                HttpStatus.PAYMENT_REQUIRED,
                Map.of("currentBalance", 100, "requiredAmount", 200));

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleBusinessException(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("INSUFFICIENT_BALANCE", error.get("code"));
        assertNotNull(error.get("details"));
        Map<String, Object> details = (Map<String, Object>) error.get("details");
        assertEquals(100, details.get("currentBalance"));
        assertEquals(200, details.get("requiredAmount"));
    }

    // ==================== MethodArgumentNotValidException ====================

    @Test
    void handleValidationException_shouldReturn400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "amount", "充值金额不能为空"));
        bindingResult.addError(new FieldError("object", "method", "支付方式不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("VALIDATION_ERROR", error.get("code"));
        assertEquals("充值金额不能为空", error.get("message"));
        assertNotNull(error.get("details"));
    }

    // ==================== AccessDeniedException ====================

    @Test
    void handleAccessDeniedException_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("拒绝访问");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("FORBIDDEN", error.get("code"));
        assertEquals("权限不足", error.get("message"));
    }

    // ==================== IllegalArgumentException ====================

    @Test
    void handleIllegalArgument_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("无效的参数值");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("BAD_REQUEST", error.get("code"));
        assertEquals("无效的参数值", error.get("message"));
    }

    // ==================== Generic Exception ====================

    @Test
    void handleGenericException_shouldReturn500() {
        Exception ex = new RuntimeException("未知错误");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("INTERNAL_ERROR", error.get("code"));
        assertEquals("服务器内部错误", error.get("message"));
    }
}
