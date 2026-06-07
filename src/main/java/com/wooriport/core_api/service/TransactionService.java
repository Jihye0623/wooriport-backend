package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.SalaryTransactionListResponseDto;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import com.wooriport.core_api.base.exception.DuplicateEventException;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;

    /**
     * transaction-events 한 건을 DB에 적재한다. (CREDIT_CARD 결제 = 출금 → 음수 저장)
     * 매칭되는 asset_number 가 없으면 null 을 반환한다.
     * 후속 반응(챌린지/급여)이 이 트랜잭션 커밋 이후에 돌도록, 컨슈머와 별개의 자기 트랜잭션으로 동작한다.
     */
    @Transactional
    public PersistedTransaction persist(TransactionEventDto event) {
        // 멱등 적재: 이미 처리한 event_id 면 중복 전달 → 예외로 알려 컨슈머가 스킵하게 한다.
        if (event.getEventId() != null && transactionRepository.existsByEventId(event.getEventId())) {
            throw new DuplicateEventException(event.getEventId());
        }

        Assets asset = assetRepository.findByAssetNumber(event.getAssetNumber())
                .orElse(null);

        if (asset == null) {
            log.warn("매칭되는 asset_number 없음 — 메시지 스킵: {}", event.getAssetNumber());
            return null;
        }

        Users user = asset.getUser();

        // CREDIT_CARD 결제는 출금이므로 음수로 적재 (양수=입금 / 음수=출금)
        long amount = -Math.abs(event.getAmount());

        Transactions transaction = Transactions.builder()
                .eventId(event.getEventId())
                .user(user)
                .asset(asset)
                .amount(amount)
                .category(event.getCategory())
                .senderName(event.getSenderName())
                .transactionAt(event.getTransactionAt())
                .build();

        try {
            // saveAndFlush 로 즉시 flush 해야 unique 충돌(동시 중복)을 여기서 잡을 수 있다.
            transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            // 동시성으로 같은 event_id 가 먼저 적재된 경우 → 중복으로 처리
            throw new DuplicateEventException(event.getEventId());
        }

        log.info("거래 적재 — user={}, asset={}, amount={}, category={}, sender={}",
                user.getName(), asset.getAssetNumber(),
                amount, event.getCategory(), event.getSenderName());

        return new PersistedTransaction(
                user.getId(),
                asset.getId(),
                user.getAutoTransferToAssetId(),
                event.getCategory(),
                event.getSenderName(),
                event.getTransactionAt(),
                Math.abs(event.getAmount()),
                event.getAmount() > 0);
    }

    @Transactional(readOnly = true)
    public SalaryTransactionListResponseDto getSalaryTransactions(UUID userId) {
        List<Transactions> txs = transactionRepository
                .findSalaryTransactionsByUserId(userId);

        List<SalaryTransactionListResponseDto.SalaryItem> items = txs.stream()
                .map(t -> SalaryTransactionListResponseDto.SalaryItem.builder()
                        .id(t.getId())
                        .assetId(t.getAsset().getId())
                        .institution(t.getAsset().getInstitution())
                        .amount(t.getAmount())
                        .category(t.getCategory())
                        .senderName(t.getSenderName())
                        .transactionAt(t.getTransactionAt().toString())
                        .build())
                .toList();

        return SalaryTransactionListResponseDto.builder()
                .salaryTransactions(items)
                .totalCount(items.size())
                .build();
    }
}