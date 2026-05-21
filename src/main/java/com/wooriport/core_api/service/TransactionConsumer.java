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
    }
}
