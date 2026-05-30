package com.charging.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final Object details;

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public BusinessException(String code, String message, HttpStatus httpStatus, Object details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Object getDetails() {
        return details;
    }

    public static BusinessException notFound(String resource, String id) {
        return new BusinessException("NOT_FOUND",
                resource + " not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException("CONFLICT", message, HttpStatus.CONFLICT);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException insufficientBalance(BigDecimal current, BigDecimal required) {
        return new BusinessException("INSUFFICIENT_BALANCE",
                "余额不足", HttpStatus.PAYMENT_REQUIRED,
                Map.of("currentBalance", current, "requiredAmount", required));
    }

    public static BusinessException accountLocked() {
        return new BusinessException("ACCOUNT_LOCKED",
                "账户已锁定", HttpStatus.LOCKED);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException tooManyRequests(String message) {
        return new BusinessException("TOO_MANY_REQUESTS", message, HttpStatus.TOO_MANY_REQUESTS);
    }

    public static BusinessException accountFrozen() {
        return new BusinessException("ACCOUNT_FROZEN",
                "账户已冻结", HttpStatus.FORBIDDEN);
    }
}