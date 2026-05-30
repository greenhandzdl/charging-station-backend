package com.charging.service.impl;

import com.charging.enums.ChargerType;
import com.charging.service.BillingService;
import com.charging.strategy.PeakPricing;
import com.charging.strategy.StandardPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final StandardPricing standardPricing;
    private final PeakPricing peakPricing;

    private static final LocalTime PEAK_START = LocalTime.of(8, 0);
    private static final LocalTime PEAK_END = LocalTime.of(22, 0);

    @Override
    public BigDecimal calculateFee(ChargerType chargerType, BigDecimal energyKwh) {
        LocalTime now = LocalTime.now();
        boolean isPeak = !now.isBefore(PEAK_START) && now.isBefore(PEAK_END);

        // Normal hours (22:00-8:00): use standard pricing
        // Peak hours (8:00-22:00): use peak pricing
        if (isPeak) {
            return peakPricing.calculatePrice(chargerType, energyKwh);
        } else {
            return standardPricing.calculatePrice(chargerType, energyKwh);
        }
    }
}