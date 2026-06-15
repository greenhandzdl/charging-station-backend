package com.charging.controller;

import com.charging.entity.Payment;
import com.charging.enums.PaymentStatus;
import com.charging.exception.BusinessException;
import com.charging.infrastructure.dto.PaymentCallbackRequest;
import com.charging.infrastructure.dto.RechargeRequest;
import com.charging.infrastructure.dto.RechargeResponse;
import com.charging.infrastructure.security.JwtUserPrincipal;
import com.charging.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController paymentController;

    private UUID userId;
    private JwtUserPrincipal userPrincipal;
    private JwtUserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(paymentService);

        userId = UUID.randomUUID();
        userPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("USER")
                .build();
        adminPrincipal = JwtUserPrincipal.builder()
                .userId(userId.toString())
                .role("ADMIN")
                .build();
    }

    @Test
    void recharge_withValidAmount_shouldReturn200() {
        RechargeRequest request = RechargeRequest.builder()
                .amount(new BigDecimal("100.00"))
                .method("alipay")
                .idempotencyKey("idem-001")
                .build();
        RechargeResponse expectedResponse = RechargeResponse.builder()
                .paymentId("pay-001")
                .redirectUrl("https://pay.example.com/redirect")
                .status("PENDING")
                .build();

        when(paymentService.recharge(userId, request)).thenReturn(expectedResponse);

        ResponseEntity<RechargeResponse> response = paymentController.recharge(request, userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("pay-001", response.getBody().getPaymentId());
        assertEquals("https://pay.example.com/redirect", response.getBody().getRedirectUrl());
        assertEquals("PENDING", response.getBody().getStatus());
        verify(paymentService).recharge(userId, request);
    }

    @Test
    void recharge_withInvalidAmount_shouldThrowBusinessException() {
        RechargeRequest request = RechargeRequest.builder()
                .amount(new BigDecimal("0.00"))
                .method("alipay")
                .idempotencyKey("idem-002")
                .build();

        when(paymentService.recharge(userId, request))
                .thenThrow(BusinessException.badRequest("充值金额必须大于0"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentController.recharge(request, userPrincipal));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("充值金额必须大于0"));
        verify(paymentService).recharge(userId, request);
    }

    @Test
    void handleCallback_withValidGatewayKey_shouldReturn200() {
        PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                .paymentId("pay-001")
                .txId("tx-123")
                .status("SUCCESS")
                .signature("valid-signature")
                .amount("100.00")
                .build();

        doNothing().when(paymentService).processCallback(callback, "valid-key");

        ResponseEntity<Map<String, String>> response = paymentController.handleCallback(callback, "valid-key");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ok", response.getBody().get("status"));
        verify(paymentService).processCallback(callback, "valid-key");
    }

    @Test
    void handleCallback_withInvalidSignature_shouldThrowBusinessException() {
        PaymentCallbackRequest callback = PaymentCallbackRequest.builder()
                .paymentId("pay-001")
                .txId("tx-123")
                .status("SUCCESS")
                .signature("invalid-signature")
                .amount("100.00")
                .build();

        doThrow(BusinessException.badRequest("回调签名验证失败"))
                .when(paymentService).processCallback(callback, "wrong-key");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentController.handleCallback(callback, "wrong-key"));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("回调签名验证失败"));
        verify(paymentService).processCallback(callback, "wrong-key");
    }

    @Test
    void queryPayments_shouldReturn200WithList() {
        Map<String, String> params = Map.of("page", "1", "size", "10");
        Payment payment1 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.SUCCESS)
                .build();
        Payment payment2 = Payment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.PENDING)
                .build();
        List<Payment> expectedPayments = List.of(payment1, payment2);

        when(paymentService.queryPayments(any(UUID.class), anyString(),
                any(), any(), any(), any(), any(), any())).thenReturn(expectedPayments);

        ResponseEntity<List<Payment>> response = paymentController.queryPayments(params, userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertEquals(payment1.getId(), response.getBody().get(0).getId());
        assertEquals(payment2.getId(), response.getBody().get(1).getId());
        verify(paymentService).queryPayments(any(UUID.class), anyString(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void listPendingPayments_shouldReturn200WithList() {
        Payment pending1 = Payment.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("200.00"))
                .status(PaymentStatus.PENDING)
                .build();
        Payment pending2 = Payment.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("300.00"))
                .status(PaymentStatus.PENDING)
                .build();
        List<Payment> expectedPending = List.of(pending1, pending2);

        when(paymentService.listPendingPayments()).thenReturn(expectedPending);

        ResponseEntity<List<Payment>> response = paymentController.listPendingPayments();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().stream().allMatch(p -> p.getStatus() == PaymentStatus.PENDING));
        verify(paymentService).listPendingPayments();
    }

    @Test
    void approvePayment_shouldReturn200() {
        UUID paymentId = UUID.randomUUID();

        doNothing().when(paymentService).approvePayment(paymentId, userId);

        ResponseEntity<Map<String, String>> response = paymentController.approvePayment(paymentId, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("approved", response.getBody().get("status"));
        verify(paymentService).approvePayment(paymentId, userId);
    }

    @Test
    void approvePayment_whenPaymentNotFound_shouldThrowBusinessException() {
        UUID paymentId = UUID.randomUUID();

        doThrow(BusinessException.notFound("Payment", paymentId.toString()))
                .when(paymentService).approvePayment(paymentId, userId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentController.approvePayment(paymentId, adminPrincipal));
        assertEquals("NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("Payment not found"));
        verify(paymentService).approvePayment(paymentId, userId);
    }

    @Test
    void rejectPayment_shouldReturn200() {
        UUID paymentId = UUID.randomUUID();

        doNothing().when(paymentService).rejectPayment(paymentId, userId, "管理员拒绝");

        ResponseEntity<Map<String, String>> response = paymentController.rejectPayment(paymentId, adminPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("rejected", response.getBody().get("status"));
        verify(paymentService).rejectPayment(paymentId, userId, "管理员拒绝");
    }

    @Test
    void rejectPayment_whenAlreadyApproved_shouldThrowBusinessException() {
        UUID paymentId = UUID.randomUUID();

        doThrow(BusinessException.conflict("该支付已审核通过，无法拒绝"))
                .when(paymentService).rejectPayment(paymentId, userId, "管理员拒绝");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentController.rejectPayment(paymentId, adminPrincipal));
        assertEquals("CONFLICT", ex.getCode());
        verify(paymentService).rejectPayment(paymentId, userId, "管理员拒绝");
    }

    @Test
    void payArrears_shouldReturn200() {
        UUID recordId = UUID.randomUUID();
        Map<String, Object> request = Map.of(
                "recordId", recordId.toString(),
                "method", "wechat"
        );

        doNothing().when(paymentService).payArrears(userId, recordId, "wechat");

        ResponseEntity<Map<String, String>> response = paymentController.payArrears(request, userPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("支付成功", response.getBody().get("message"));
        verify(paymentService).payArrears(userId, recordId, "wechat");
    }

    @Test
    void payArrears_withInvalidRecordId_shouldThrowIllegalArgumentException() {
        Map<String, Object> request = Map.of(
                "recordId", "not-a-uuid",
                "method", "wechat"
        );

        assertThrows(IllegalArgumentException.class,
                () -> paymentController.payArrears(request, userPrincipal));
    }
}
