package com.charging.infrastructure.connector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * HTTP 充电桩连接器实现。
 * 当前为 stub 实现：仅打印日志并返回 true。
 * 后续升级为真实 HTTP/WebSocket 通信。
 */
@Slf4j
@Component
public class HttpChargerConnector implements ChargerConnector {

    @Override
    public boolean notifyStart(String chargerCode, UUID chargeRecordId) {
        log.info("[ChargerConnector] notifyStart: chargerCode={}, recordId={}", chargerCode, chargeRecordId);
        // TODO: 替换为真实 HTTP 调用通知 Mock 充电桩
        return true;
    }

    @Override
    public boolean notifyStop(String chargerCode, UUID chargeRecordId) {
        log.info("[ChargerConnector] notifyStop: chargerCode={}, recordId={}", chargerCode, chargeRecordId);
        // TODO: 替换为真实 HTTP 调用通知 Mock 充电桩
        return true;
    }

    @Override
    public boolean isOnline(String chargerCode) {
        log.debug("[ChargerConnector] isOnline: chargerCode={}", chargerCode);
        // TODO: 替换为真实心跳检测
        return true;
    }
}
