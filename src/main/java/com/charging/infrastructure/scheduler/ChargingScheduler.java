package com.charging.infrastructure.scheduler;

import com.charging.entity.Charger;
import com.charging.mapper.ChargeRecordMapper;
import com.charging.mapper.ChargerMapper;
import com.charging.service.ChargingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
    private final ChargerMapper chargerMapper;
    private final ChargeRecordMapper chargeRecordMapper;

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

    /**
     * 每 60 秒扫描充电桩心跳，标记离线，并自动停止离线桩上的充电记录。
     */
    @Scheduled(fixedDelay = 60000)
    public void checkOfflineChargers() {
        log.debug("Scanning for offline chargers...");
        // Find all chargers with heartbeat > 60s ago or null
        List<Charger> allChargers = chargerMapper.findAll();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(60);
        int markedOffline = 0;
        int stoppedCharges = 0;

        for (Charger charger : allChargers) {
            if (charger.getLastHeartbeatAt() == null || charger.getLastHeartbeatAt().isBefore(cutoff)) {
                // Charger is offline - mark it if currently ONLINE
                if ("ONLINE".equals(charger.getOnlineStatus()) || charger.getOnlineStatus() == null) {
                    chargerMapper.markOffline(charger.getId());
                    markedOffline++;
                    log.warn("Charger {} (id={}) marked OFFLINE - last heartbeat: {}",
                            charger.getChargerCode(), charger.getId(), charger.getLastHeartbeatAt());

                    // 自动停止该离线桩上所有进行中的充电
                    int stopped = chargingService.forceStopByChargerId(charger.getId(), "CHARGER_OFFLINE");
                    if (stopped > 0) {
                        stoppedCharges += stopped;
                        log.warn("Auto-stopped {} charge(s) on offline charger {} (id={})",
                                stopped, charger.getChargerCode(), charger.getId());
                    }
                }
            }
        }
        if (markedOffline > 0 || stoppedCharges > 0) {
            log.info("Offline scan complete: {} charger(s) marked OFFLINE, {} charge(s) auto-stopped", markedOffline, stoppedCharges);
        }
    }
}