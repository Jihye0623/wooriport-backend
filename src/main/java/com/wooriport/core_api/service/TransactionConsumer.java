package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * transaction-events 컨슈머. 메시지 수신/파싱 후 처리를 각 서비스에 위임만 한다.
 *  1) 적재     : TransactionService.persist        — 핵심 사실, 자기 트랜잭션으로 커밋
 *  2) 챌린지   : ChallengeService.updateProgress    — 적재 커밋 후 best-effort
 *  3) 급여     : SalaryService.handleIfSalary       — 적재 커밋 후 best-effort
 *
 * 부수효과(2,3)는 적재가 커밋된 뒤에 돌며, 실패해도 적재나 서로에게 영향을 주지 않는다(best-effort + 로깅).
 * 단, Kafka at-least-once 특성상 중복 전달 시 적재/카운트가 중복될 수 있다(현재는 미보장 — 의도적 트레이드오프).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionConsumer {

    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;
    private final ChallengeService challengeService;
    private final SalaryService salaryService;

    @KafkaListener(topics = "transaction-events", groupId = "approval-detect-group")
    public void consume(String message) {
        TransactionEventDto event;
        try {
            event = objectMapper.readValue(message, TransactionEventDto.class);
        } catch (Exception e) {
            log.error("transaction-events 메시지 파싱 실패: {}", message, e);
            return;
        }

        // 1. DB 적재 (자기 트랜잭션으로 커밋)
        PersistedTransaction tx = transactionService.persist(event);
        if (tx == null) return; // 매칭 asset 없음 → 스킵

        // 2. 챌린지 진행 업데이트 (Redis) — best-effort
        try {
            challengeService.updateProgress(tx.userId(), tx.category(), tx.rawAmount());
        } catch (Exception e) {
            log.error("[TransactionConsumer] 챌린지 진행 업데이트 실패 — userId: {}, 사유: {}", tx.userId(), e.getMessage());
        }

        // 3. 급여 감지 → 이체 계획 생성 — best-effort
        try {
            salaryService.handleIfSalary(tx);
        } catch (Exception e) {
            log.error("[TransactionConsumer] 급여 처리 실패 — userId: {}, 사유: {}", tx.userId(), e.getMessage());
        }
    }
}
