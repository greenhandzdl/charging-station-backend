package com.charging.strategy;

import com.charging.enums.ChargerType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * 峰时定价策略
 * 高峰期 (8:00-22:00): 上浮 50%
 * 低谷期 (22:00-8:00): 下浮 30%
 */
@Component
public class PeakPricing implements PricingStrategy {

    private static final BigDecimal FAST_BASE = new BigDecimal("1.50");
    private static final BigDecimal SLOW_BASE = new BigDecimal("0.80");
    private static final BigDecimal PEAK_MULTIPLIER = new BigDecimal("1.50");
    private static final BigDecimal OFF_PEAK_MULTIPLIER = new BigDecimal("0.70");

    private static final LocalTime PEAK_START = LocalTime.of(8, 0);
    private static final LocalTime PEAK_END = LocalTime.of(22, 0);

    @Override
    public BigDecimal calculatePrice(ChargerType type, BigDecimal energyKwh) {
        BigDecimal basePrice = (type == ChargerType.FAST) ? FAST_BASE : SLOW_BASE;
        LocalTime now = LocalTime.now();

        boolean isPeak = !now.isBefore(PEAK_START) && now.isBefore(PEAK_END);
        BigDecimal multiplier = isPeak ? PEAK_MULTIPLIER : OFF_PEAK_MULTIPLIER;

        return basePrice.multiply(multiplier).multiply(energyKwh)
                .setScale(2, RoundingMode.HALF_UP);
    }
}