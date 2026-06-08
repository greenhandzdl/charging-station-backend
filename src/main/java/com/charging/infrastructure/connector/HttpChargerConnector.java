package com.charging.infrastructure.connector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * HTTP 充电桩连接器实现。
 * 通过 HTTP POST 通知 Mock 充电桩（Swing 客户端）。
 */
@Slf4j
@Component
public class HttpChargerConnector implements ChargerConnector {

    @Value("${charger.mock.base-url:http://localhost:8081}")
    private String mockChargerBaseUrl;

    private final HttpClient httpClient;

    public HttpChargerConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean notifyStart(String chargerCode, UUID chargeRecordId) {
        log.info("[ChargerConnector] notifyStart: chargerCode={}, recordId={}", chargerCode, chargeRecordId);
        try {
            String url = mockChargerBaseUrl + "/api/notify/start";
            String json = String.format(
                "{\"chargerCode\":\"%s\",\"recordId\":\"%s\"}",
                chargerCode, chargeRecordId
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("notifyStart response: {}", response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to notify charger {} (start)", chargerCode, e);
            return false;
        }
    }

    @Override
    public boolean notifyStop(String chargerCode, UUID chargeRecordId) {
        log.info("[ChargerConnector] notifyStop: chargerCode={}, recordId={}", chargerCode, chargeRecordId);
        try {
            String url = mockChargerBaseUrl + "/api/notify/stop";
            String json = String.format(
                "{\"chargerCode\":\"%s\",\"recordId\":\"%s\"}",
                chargerCode, chargeRecordId
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("notifyStop response: {}", response.statusCode());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to notify charger {} (stop)", chargerCode, e);
            return false;
        }
    }

    @Override
    public boolean isOnline(String chargerCode) {
        log.debug("[ChargerConnector] isOnline: chargerCode={}", chargerCode);
        try {
            String url = mockChargerBaseUrl + "/api/health/" + chargerCode;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Charger {} is not reachable: {}", chargerCode, e.getMessage());
            return false;
        }
    }
}
