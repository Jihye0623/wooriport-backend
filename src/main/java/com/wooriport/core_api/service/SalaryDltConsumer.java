package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.SalaryRetryMessage;
import com.wooriport.core_api.domain.FailedSalaryAction;
import com.wooriport.core_api.repository.FailedSalaryActionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 급여 DLT 컨슈머 — DLT 로 격리된 메시지를 failed_salary_actions 테이블에 적재(감사기록).
 * 관리자 대시보드에서 조회 후 redrive 한다. (DLT 토픽 메시지는 그대로 남아 Kafka UI 로도 확인 가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryDltConsumer {

    private final ObjectMapper objectMapper;
    private final FailedSalaryActionRepository repository;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = SalaryRetryConsumer.DLT_TOPIC, groupId = "salary-dlt-group")
    public void consume(String message) throws Exception {
        SalaryRetryMessage m = objectMapper.readValue(message, SalaryRetryMessage.class);

        FailedSalaryAction saved = repository.save(FailedSalaryAction.builder()
                .userId(m.tx().userId())
                .payload(objectMapper.writeValueAsString(m.tx()))   // 재투입용 PersistedTransaction
                .attempts(m.attempt())
                .lastError("급여 재처리 " + m.attempt() + "회 실패 → DLT 격리")
                .status(FailedSalaryAction.Status.PENDING)
                .build());

        meterRegistry.counter("tx.salary.dlt.persisted").increment();
        log.warn("salary_dlt_persisted",
                kv("event_type", "salary_dlt_persisted"),
                kv("failed_id",  saved.getId().toString()),
                kv("user_id",    m.tx().userId().toString()),
                kv("attempts",   m.attempt()));
    }
}
