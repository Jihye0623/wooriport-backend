package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.SalaryTransactionListResponseDto;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        Assets asset = assetRepository.findByAssetNumber(event.getAssetNumber())
                .orElse(null);

        if (asset == null) {
            log.warn("매칭되는 asset_number 없음 — 메시지 스킵: {}", event.getAssetNumber());
            return null;
        }

        Users user = asset.getUser();

        // 급여 등 입금 카테고리는 양수, 카드 결제 등 출금은 음수로 적재 (양수=입금 / 음수=출금)
        boolean income = isIncomeCategory(event.getCategory());
        long amount = income ? Math.abs(event.getAmount()) : -Math.abs(event.getAmount());

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

        return new PersistedTransaction(
                user.getId(),
                asset.getId(),
                user.getAutoTransferToAssetId(),
                event.getCategory(),
                event.getSenderName(),
                event.getTransactionAt(),
                Math.abs(event.getAmount()),
                income);
    }

    // 급여/월급/임금/salary 등 입금 카테고리 판별 (SalaryService.isSalary 와 동일 기준)
    private static boolean isIncomeCategory(String category) {
        if (category == null) return false;
        return category.contains("급여") || category.contains("월급")
                || category.contains("임금") || category.contains("salary");
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