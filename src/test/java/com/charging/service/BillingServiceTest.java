package com.charging.service;

import com.charging.enums.ChargerType;
import com.charging.service.impl.BillingServiceImpl;
import com.charging.strategy.PeakPricing;
import com.charging.strategy.StandardPricing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTest {

    private BillingService billingService;

    @BeforeEach
    void setUp() {
        billingService = new BillingServiceImpl(new StandardPricing(), new PeakPricing());
    }

    @Test
    void calculateFee_shouldReturnPositive_fastCharger() {
        BigDecimal fee = billingService.calculateFee(ChargerType.FAST, new BigDecimal("10.00"));
        assertTrue(fee.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateFee_shouldReturnPositive_slowCharger() {
        BigDecimal fee = billingService.calculateFee(ChargerType.SLOW, new BigDecimal("10.00"));
        assertTrue(fee.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateFee_slowCheaperThanFast() {
        BigDecimal fastFee = billingService.calculateFee(ChargerType.FAST, new BigDecimal("10.00"));
        BigDecimal slowFee = billingService.calculateFee(ChargerType.SLOW, new BigDecimal("10.00"));
        assertTrue(slowFee.compareTo(fastFee) < 0);
    }

    @Test
    void calculateFee_zeroEnergy() {
        BigDecimal fee = billingService.calculateFee(ChargerType.FAST, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(fee));
    }

    @Test
    void calculateFee_rounding() {
        BigDecimal fee = billingService.calculateFee(ChargerType.FAST, new BigDecimal("1.234"));
        assertNotNull(fee);
        assertEquals(2, fee.scale());
    }
}