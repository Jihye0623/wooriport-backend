package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionConsumer {

    private final ObjectMapper objectMapper;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final TransferPlanService transferPlanService;
    private final ChallengeService challengeService;

    @KafkaListener(topics = "transaction-events", groupId = "approval-detect-group")
    @Transactional
    public void consume(String message) {
        TransactionEventDto event;
        try {
            event = objectMapper.readValue(message, TransactionEventDto.class);
        } catch (Exception e) {
            log.error("transaction-events 메시지 파싱 실패: {}", message, e);
            return;
        }

        Assets asset = assetRepository.findByAssetNumber(event.getAssetNumber())
                .orElse(null);

        if (asset == null) {
            log.warn("매칭되는 asset_number 없음 — 메시지 스킵: {}", event.getAssetNumber());
            return;
        }

        Users user = asset.getUser();

        // CREDIT_CARD 결제는 출금이므로 음수로 적재 (양수=입금 / 음수=출금)
        long amount = -Math.abs(event.getAmount());

        Transactions transaction = Transactions.builder()
                .user(user)
                .asset(asset)
                .amount(amount)
                .category(event.getCategory())
                .senderName(event.getSenderName())
                .transactionAt(event.getTransactionAt())
                .build();

        transactionRepository.save(transaction);

        log.info("거래 적재 — user={}, asset={}, amount={}, category={}, sender={}",
                user.getName(), asset.getAssetNumber(),
                amount, event.getCategory(), event.getSenderName());

        // 챌린지 진행 업데이트
        try {
            challengeService.updateProgress(user.getId(), event.getCategory(), Math.abs(event.getAmount()));
        } catch (Exception e) {
            log.error("[TransactionConsumer] 챌린지 진행 업데이트 실패 — userId: {}, 사유: {}", user.getId(), e.getMessage());
        }

        // 급여 입금 감지 → 이체 계획 자동 생성
        if (isSalary(event.getCategory())
                && event.getAmount() > 0
                && asset.getId().equals(user.getAutoTransferToAssetId())) {
            try {
                transferPlanService.generateFromSalary(user.getId());
                log.info("[TransactionConsumer] 급여 감지 → 이체 계획 생성 — userId: {}", user.getId());
            } catch (Exception e) {
                log.error("[TransactionConsumer] 이체 계획 생성 실패 — userId: {}, 사유: {}", user.getId(), e.getMessage());
            }
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
