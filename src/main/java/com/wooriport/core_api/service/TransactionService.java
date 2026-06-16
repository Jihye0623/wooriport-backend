package com.wooriport.core_api.service;


import com.wooriport.core_api.base.dto.transaction.BatchPersistResult;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        // 급여 등 입금 카테고리는 양수, 카드 결제 등 출금은 음수로 적재 (양수=입금 / 음수=출금)
        boolean income = isIncomeCategory(event.getCategory());
        long amount = income ? Math.abs(event.getAmount()) : -Math.abs(event.getAmount());

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
                income);
    }

    // 급여/월급/임금/salary 등 입금 카테고리 판별 (SalaryService.isSalary 와 동일 기준)
    private static boolean isIncomeCategory(String category) {
        if (category == null) return false;
        return category.contains("급여") || category.contains("월급")
                || category.contains("임금") || category.contains("salary");
    }

    /**
     * Phase 2: poll 단위 배치 적재.
     * 단건 persist() 의 "메시지당 SELECT + saveAndFlush" 를 제거하고
     * ① intra-batch dedup → ② DB dedup 1쿼리 → ③ asset 일괄조회 1쿼리 → ④ saveAll(Hibernate 배치 INSERT) 로 교체.
     * poll 전체가 단일 트랜잭션: 실패 시 Kafka 오프셋 미커밋 → 재시도 → dedup 이 정합성 보장.
     */
    @Transactional
    public BatchPersistResult batchPersist(List<TransactionEventDto> events) {
        if (events.isEmpty()) return new BatchPersistResult(List.of(), 0);

        // ① intra-batch dedup: poll 안에서 같은 event_id 가 중복으로 온 경우 (첫 번째만 통과)
        Map<String, TransactionEventDto> seenMap = new LinkedHashMap<>();
        List<TransactionEventDto> nullIdEvents = new ArrayList<>();
        int batchDups = 0;
        for (TransactionEventDto e : events) {
            if (e.getEventId() == null) {
                nullIdEvents.add(e); // event_id 없으면 dedup 불가 → 그대로 통과
            } else if (seenMap.putIfAbsent(e.getEventId(), e) != null) {
                batchDups++;
            }
        }

        // ② DB dedup: 이미 적재된 event_id 를 1쿼리로 필터 (이전 poll 에서 처리된 것)
        Set<String> existingIds = seenMap.isEmpty()
                ? Set.of()
                : transactionRepository.findExistingEventIds(seenMap.keySet());
        List<TransactionEventDto> newEvents = new ArrayList<>(nullIdEvents);
        int dbDups = 0;
        for (Map.Entry<String, TransactionEventDto> entry : seenMap.entrySet()) {
            if (existingIds.contains(entry.getKey())) dbDups++;
            else newEvents.add(entry.getValue());
        }
        int totalDups = batchDups + dbDups;

        if (newEvents.isEmpty()) return new BatchPersistResult(List.of(), totalDups);

        // ③ asset 일괄조회: N+1 없이 1쿼리 (JOIN FETCH user 포함)
        Set<String> assetNumbers = newEvents.stream()
                .map(TransactionEventDto::getAssetNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Assets> assetMap = assetRepository.findByAssetNumberIn(assetNumbers)
                .stream()
                .collect(Collectors.toMap(Assets::getAssetNumber, a -> a));

        // ④ 엔티티 빌드 + saveAll (Hibernate 가 jdbc.batch_size 단위로 배치 INSERT)
        List<Transactions> toSave = new ArrayList<>();
        List<PersistedTransaction> results = new ArrayList<>();
        for (TransactionEventDto event : newEvents) {
            Assets asset = assetMap.get(event.getAssetNumber());
            if (asset == null) {
                log.warn("매칭되는 asset_number 없음 — 스킵: {}", event.getAssetNumber());
                continue;
            }
            Users user = asset.getUser();
            // 목서버는 수입/지출 모두 amount 를 양수로 발행한다 → 급여류 카테고리면 입금(+), 그 외는 지출(−)
            boolean isIncome = isSalaryCategory(event.getCategory());
            long amount = isIncome ? Math.abs(event.getAmount()) : -Math.abs(event.getAmount());

            toSave.add(Transactions.builder()
                    .eventId(event.getEventId())
                    .user(user)
                    .asset(asset)
                    .amount(amount)
                    .category(event.getCategory())
                    .senderName(event.getSenderName())
                    .transactionAt(event.getTransactionAt())
                    .build());

            results.add(new PersistedTransaction(
                    user.getId(),
                    asset.getId(),
                    user.getAutoTransferToAssetId(),
                    event.getCategory(),
                    event.getSenderName(),
                    event.getTransactionAt(),
                    Math.abs(event.getAmount()),
                    isIncome));
        }

        transactionRepository.saveAll(toSave);

        log.info("배치 적재 완료 — poll={}, 신규={}, 중복(배치내={}, DB={})",
                events.size(), results.size(), batchDups, dbDups);
        return new BatchPersistResult(results, totalDups);
    }

    // 급여류 카테고리 = 입금. SalaryService.isSalary 와 동일 기준.
    private boolean isSalaryCategory(String category) {
        if (category == null) return false;
        return category.contains("급여")
                || category.contains("월급")
                || category.contains("임금")
                || category.contains("salary");
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