package com.charging.strategy;

import com.charging.enums.ChargerType;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculatePrice(ChargerType type, BigDecimal energyKwh);
}