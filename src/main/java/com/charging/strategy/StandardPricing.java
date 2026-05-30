package com.charging.strategy;

import com.charging.enums.ChargerType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 标准定价策略
 * FAST: 1.5 元/kWh
 * SLOW: 0.8 元/kWh
 */
@Component
public class StandardPricing implements PricingStrategy {

    private static final BigDecimal FAST_PRICE = new BigDecimal("1.50");
    private static final BigDecimal SLOW_PRICE = new BigDecimal("0.80");

    @Override
    public BigDecimal calculatePrice(ChargerType type, BigDecimal energyKwh) {
        BigDecimal unitPrice = (type == ChargerType.FAST) ? FAST_PRICE : SLOW_PRICE;
        return unitPrice.multiply(energyKwh).setScale(2, RoundingMode.HALF_UP);
    }
}