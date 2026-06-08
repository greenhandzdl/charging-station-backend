package com.charging.infrastructure.connector;

import java.util.UUID;

/**
 * 充电桩连接器接口。
 * 负责 Spring Boot 与充电桩硬件/模拟器之间的通信。
 */
public interface ChargerConnector {

    /**
     * 通知充电桩开始充电。
     *
     * @param chargerCode   充电桩编号
     * @param chargeRecordId 充电记录ID
     * @return true 表示充电桩已回复 ACK
     */
    boolean notifyStart(String chargerCode, UUID chargeRecordId);

    /**
     * 通知充电桩结束充电。
     *
     * @param chargerCode   充电桩编号
     * @param chargeRecordId 充电记录ID
     * @return true 表示充电桩已确认
     */
    boolean notifyStop(String chargerCode, UUID chargeRecordId);

    /**
     * 检查充电桩是否在线。
     *
     * @param chargerCode 充电桩编号
     * @return true 表示在线
     */
    boolean isOnline(String chargerCode);
}
