package com.charging.service;

import com.charging.enums.ChargerType;

import java.math.BigDecimal;

public interface BillingService {
    BigDecimal calculateFee(ChargerType chargerType, BigDecimal energyKwh);
}