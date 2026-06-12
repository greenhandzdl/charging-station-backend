package com.charging.controller;

import com.charging.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {} - {}", e.getCode(), e.getMessage());
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> error = new HashMap<>();
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        if (e.getDetails() != null) {
            error.put("details", e.getDetails());
        }
        body.put("error", error);
        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        // 取第一个字段错误作为友好提示
        String firstError = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(msg -> msg != null)
                .findFirst()
                .orElse("请求参数校验失败");
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of(
                "code", "VALIDATION_ERROR",
                "message", firstError,
                "details", fieldErrors
        ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of(
                "code", "FORBIDDEN",
                "message", "权限不足"
        ));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of(
                "code", "BAD_REQUEST",
                "message", e.getMessage()
        ));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unexpected error", e);
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of(
                "code", "INTERNAL_ERROR",
                "message", "服务器内部错误"
        ));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}