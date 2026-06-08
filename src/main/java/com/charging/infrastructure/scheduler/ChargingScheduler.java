package com.charging.infrastructure.scheduler;

import com.charging.service.ChargingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 充电管理定时任务
 * 定期检查进行中的充电记录，当用户余额不足时自动停止充电。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ChargingScheduler {

    private final ChargingService chargingService;

    /**
     * 每 30 秒检查一次余额不足的充电记录并自动停止。
     */
    @Scheduled(fixedDelay = 30000)
    public void checkInsufficientBalance() {
        int stopped = chargingService.autoStopOnInsufficientBalance();
        if (stopped > 0) {
            log.info("Scheduler auto-stopped {} charge(s) due to insufficient balance", stopped);
        }
    }
}