package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.BatchPersistResult;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.SalaryRetryMessage;
import com.wooriport.core_api.base.dto.transaction.TransactionEventDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Phase 2: @KafkaListener(batch=true) — poll 단위 배치 처리.
 *
 * Phase 1 의 "메시지당 SELECT + saveAndFlush" 병목을
 * batchPersist() 로 대체:
 *  ① intra-batch dedup (poll 내 중복 첫 번째만 통과)
 *  ② DB dedup 1쿼리 (findExistingEventIds)
 *  ③ asset 일괄 조회 1쿼리 (findByAssetNumberIn)
 *  ④ saveAll (Hibernate jdbc.batch_size 단위 배치 INSERT)
 *
 * poll 전체가 단일 @Transactional: 실패 시 오프셋 미커밋 → Kafka 재시도 → dedup 이 정합성 보장.
 * 챌린지/급여 후속처리는 Phase 1 과 동일하게 per-item best-effort 유지.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionConsumer {

    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;
    private final ChallengeService challengeService;
    private final SalaryService salaryService;
    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = "transaction-events",
            groupId = "approval-detect-group",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(List<String> messages) {
        if (messages.isEmpty()) return;

        // 1. 파싱: 실패한 메시지는 로그 후 스킵, 나머지 계속 처리
        List<TransactionEventDto> events = new ArrayList<>(messages.size());
        for (String raw : messages) {
            try {
                events.add(objectMapper.readValue(raw, TransactionEventDto.class));
            } catch (Exception e) {
                meterRegistry.counter("tx.parse.failed").increment();
                log.error("kafka_parse_failed",
                        kv("event_type", "kafka_parse_failed"),
                        kv("topic",      "transaction-events"),
                        kv("error",      e.getMessage()));
            }
        }

        meterRegistry.summary("tx.batch.size").record(messages.size());     // poll 크기 기록
        meterRegistry.counter("tx.consumed").increment(events.size());      // 처리 건수 누적

        // E2E 지연: poll 진입 시각 기준 (전체 배치를 대표하는 "now")
        long now = System.currentTimeMillis();
        for (TransactionEventDto event : events) {
            if (event.getProducedAt() != null) {
                meterRegistry.timer("tx.e2e.latency")
                        .record(now - event.getProducedAt(), TimeUnit.MILLISECONDS);    // 지금 시각 - 발행 시간 : E2E 지연 그래프
            }
        }

        // 2. 배치 적재 (dedup + saveAll, poll 단위 트랜잭션)
        Timer.Sample persistSample = Timer.start(meterRegistry);
        BatchPersistResult result;
        try {
            result = transactionService.batchPersist(events);
        } catch (Exception e) {
            log.error("batch_persist_failed",
                    kv("event_type", "batch_persist_failed"),
                    kv("topic",      "transaction-events"),
                    kv("poll_size",  messages.size()),
                    kv("error",      e.getMessage()));
            throw e; // 오프셋 미커밋 → Kafka 재시도
        } finally {
            persistSample.stop(meterRegistry.timer("tx.persist"));
        }

        if (result.duplicates() > 0) {
            meterRegistry.counter("tx.duplicate.detected").increment(result.duplicates());
            log.info("kafka_batch_dups_skipped",
                    kv("event_type", "kafka_batch_dups_skipped"),
                    kv("count",      result.duplicates()));
        }

        // 3. 후속처리: userId 기준으로 그룹핑 후 병렬 실행 (Phase 2: 3 유저 → 3 병렬 스트림)
        //    같은 userId 내에서는 직렬 유지(Redis increment 순서 보장), 다른 userId끼리는 병렬
        result.persisted().stream()
                .collect(Collectors.groupingBy(PersistedTransaction::userId))
                .values()
                .parallelStream()
                .forEach(txList -> txList.forEach(tx -> {
                    log.info("kafka_consumed",
                            kv("event_type", "kafka_consumed"),
                            kv("topic",      "transaction-events"),
                            kv("user_id",    tx.userId().toString()));
                    processChallenge(tx);
                    processSalary(tx);
                }));
    }

    private void processChallenge(PersistedTransaction tx) {
        // Phase 2: TX-free 사전 체크 — 챌린지 없는 유저는 REQUIRES_NEW TX 오버헤드 없이 즉시 리턴
        if (!challengeService.hasActiveChallenge(tx.userId())) return;
        Timer.Sample sample = Timer.start(meterRegistry);
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
            sample.stop(meterRegistry.timer("tx.challenge"));
        }
    }

    private void processSalary(PersistedTransaction tx) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            salaryService.handleIfSalary(tx);
        } catch (Exception e) {
            meterRegistry.counter("tx.salary.failed").increment();
            publishSalaryRetry(tx, e);
        } finally {
            sample.stop(meterRegistry.timer("tx.salary"));
        }
    }

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
