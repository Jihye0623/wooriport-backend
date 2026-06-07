package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.SalaryRetryMessage;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import com.wooriport.core_api.base.exception.DuplicateEventException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final MeterRegistry meterRegistry;  // 실험 측정 하네스 (Phase 0~)
    private final KafkaTemplate<String, String> kafkaTemplate;  // Phase 1: 급여 재처리 토픽 발행

    @KafkaListener(topics = "transaction-events", groupId = "approval-detect-group")
    public void consume(String message) {
        TransactionEventDto event;
        try {
            event = objectMapper.readValue(message, TransactionEventDto.class);
        } catch (Exception e) {
            log.error("kafka_consume_failed",
                    kv("event_type", "kafka_consume_failed"),
                    kv("topic",      "transaction-events"),
                    kv("fail_reason", "메시지 파싱 실패"),
                    kv("error",       e.getMessage()));
            return;
        }

        meterRegistry.counter("tx.consumed").increment();
        // E2E 지연: 발행(producedAt) → 컨슈머 진입 시점
        if (event.getProducedAt() != null) {
            meterRegistry.timer("tx.e2e.latency")
                    .record(System.currentTimeMillis() - event.getProducedAt(), TimeUnit.MILLISECONDS);
        }

        // 1. DB 적재 (자기 트랜잭션으로 커밋). 멱등: 중복 event_id 는 DuplicateEventException → 스킵.
        Timer.Sample persistSample = Timer.start(meterRegistry);
        PersistedTransaction tx;
        try {
            tx = transactionService.persist(event);
        } catch (DuplicateEventException e) {
            meterRegistry.counter("tx.duplicate.detected").increment();
            log.info("kafka_duplicate_skipped",
                    kv("event_type", "kafka_duplicate_skipped"),
                    kv("topic",      "transaction-events"),
                    kv("event_id",   event.getEventId()));
            return;
        } finally {
            persistSample.stop(meterRegistry.timer("tx.persist"));
        }
        if (tx == null) return; // 매칭 asset 없음 → 스킵

        log.info("kafka_consumed",
                kv("event_type", "kafka_consumed"),
                kv("topic",      "transaction-events"),
                kv("user_id",    tx.userId().toString()));

        // 2. 챌린지 진행 업데이트 (Redis 증분) — 실패 시 증분 replay 대신 DB 재계산(2-2)
        Timer.Sample challengeSample = Timer.start(meterRegistry);
        try {
            challengeService.updateProgress(tx.userId(), tx.category(),
                    tx.senderName(), tx.transactionAt(), tx.rawAmount());
        } catch (Exception e) {
            meterRegistry.counter("tx.challenge.failed").increment();
            log.error("[TransactionConsumer] 챌린지 증분 실패 → DB 재계산 — userId: {}, 사유: {}", tx.userId(), e.getMessage());
            try {
                challengeService.recomputeFromDb(tx.userId());
                meterRegistry.counter("tx.challenge.recomputed").increment();
            } catch (Exception re) {
                log.error("[TransactionConsumer] 챌린지 DB 재계산도 실패 — userId: {}, 사유: {}", tx.userId(), re.getMessage());
            }
        } finally {
            challengeSample.stop(meterRegistry.timer("tx.challenge"));
        }

        // 3. 급여 감지 → 이체 계획 생성. 실패 시 유실 불가 액션이므로 재처리 토픽으로 발행(1-1)
        Timer.Sample salarySample = Timer.start(meterRegistry);
        try {
            salaryService.handleIfSalary(tx);
        } catch (Exception e) {
            meterRegistry.counter("tx.salary.failed").increment();
            publishSalaryRetry(tx, e);
        } finally {
            salarySample.stop(meterRegistry.timer("tx.salary"));
        }
    }

    /** 급여 처리 실패분을 재처리 토픽으로 발행 (유실 방지). */
    private void publishSalaryRetry(PersistedTransaction tx, Exception cause) {
        try {
            String payload = objectMapper.writeValueAsString(new SalaryRetryMessage(tx, 1));
            kafkaTemplate.send(SalaryRetryConsumer.RETRY_TOPIC, tx.userId().toString(), payload);
            meterRegistry.counter("tx.salary.retry").increment();
            log.warn("[TransactionConsumer] 급여 처리 실패 → 재처리 토픽 발행 — userId: {}, 사유: {}",
                    tx.userId(), cause.getMessage());
        } catch (Exception pubEx) {
            log.error("[TransactionConsumer] 급여 재처리 발행 실패 — userId: {}, 사유: {}", tx.userId(), pubEx.getMessage());
        }
    }
}
