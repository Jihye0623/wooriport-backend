package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Notifications;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.UserRepository;
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
    private final TransferPlanService transferPlanService;

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
                    "이상 소비 감지 🚨",
                    content);

            log.info("[AlertConsumer] 이상소비 알림 — userId: {}, content: {}", userId, content);

        } catch (Exception e) {
            log.error("[AlertConsumer] 처리 실패 — 사유: {}, 메시지: {}", e.getMessage(), message);
        }
    }

    // ──────────────────────────────────────
    // 거래 내역 적재 + 급여 감지
    // 기존 TransactionConsumer와 통합 가능
    // ──────────────────────────────────────
    @KafkaListener(topics = "transaction-events", groupId = "approval-detect-group")
    public void consumeTransaction(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            String category = (String) event.get("category");
            Object amountObj  = event.get("amount");
            long   amount     = amountObj != null ? ((Number) amountObj).longValue() : 0L;

            // 급여 입금 감지
            if (isSalary(category) && amount > 0) {
                String assetNumber = (String) event.get("asset_number");

                Assets asset = assetRepository.findByAssetNumber(assetNumber).orElse(null);
                if (asset == null) return;

                UUID userId = asset.getUser().getId();

                // 이체 계획 자동 생성 (트랜잭션 분리)
                try {
                    transferPlanService.generateFromSalary(userId);
                    log.info("[AlertConsumer] 급여 감지 → 이체 계획 생성 — userId: {}", userId);
                } catch (Exception e) {
                    // generate 실패해도 거래 적재는 유지
                    log.error("[AlertConsumer] 이체 계획 생성 실패 — userId: {}, 사유: {}",
                            userId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[AlertConsumer] transaction 처리 실패 — 사유: {}", e.getMessage());
        }
    }

    private boolean isSalary(String category) {
        if (category == null) return false;
        return category.contains("급여")
                || category.contains("월급")
                || category.contains("임금")
                || category.contains("salary");
    }
}