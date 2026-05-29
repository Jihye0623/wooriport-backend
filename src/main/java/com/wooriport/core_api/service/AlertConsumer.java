package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class AlertConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final AssetRepository assetRepository;

    // ──────────────────────────────────────
    // Flask 이상 소비 감지 알림
    // Flask → [alert.triggered] 토픽
    // ──────────────────────────────────────
    @KafkaListener(topics = "anomaly-alerts", groupId = "alert-group")  // ← 토픽명 변경
    public void consumeAlert(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            String assetNumber = (String) event.get("asset_number");
            String content     = (String) event.get("content");

            // asset_number → 유저 찾기
            Assets asset = assetRepository.findByAssetNumber(assetNumber).orElse(null);
            if (asset == null) {
                log.warn("[AlertConsumer] asset_number 매핑 실패 — {}", assetNumber);
                return;
            }

            UUID userId = asset.getUser().getId();

            // DB 저장 + SSE 전송
            notificationService.saveAndSend(
                    userId,
                    Notifications.NotificationType.SPENDING_TREND,
                    "밸런싱 붕괴 조짐이 보여요",
                    "이번 달 소비 속도가 빠르게 올라가고 있어요!",
                    content);

            log.info("[AlertConsumer] 이상소비 알림 — userId: {}, content: {}", userId, content);

        } catch (Exception e) {
            log.error("[AlertConsumer] 처리 실패 — 사유: {}, 메시지: {}", e.getMessage(), message);
        }
    }

}