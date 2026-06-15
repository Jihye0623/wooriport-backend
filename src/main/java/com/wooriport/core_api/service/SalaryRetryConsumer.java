package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.SalaryRetryMessage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Phase 1 (1-1): 급여 처리(유실 불가 액션) 재처리 컨슈머.
 * 메인 컨슈머에서 급여 처리가 실패하면 transaction-events.salary.retry 로 발행되고,
 * 여기서 급여 스텝만 다시 실행한다. handleIfSalary 는 멱등(미확인 플랜 delete+재생성)이라 중복 실행해도 안전.
 * MAX_ATTEMPTS 초과 시 DLT 로 격리(유실 아님 — 수동 점검/재투입 가능).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryRetryConsumer {

    public static final String RETRY_TOPIC = "transaction-events.salary.retry";
    public static final String DLT_TOPIC   = "transaction-events.salary.DLT";
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final SalaryService salaryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = RETRY_TOPIC, groupId = "salary-retry-group")
    public void retry(String message) throws Exception {
        SalaryRetryMessage m = objectMapper.readValue(message, SalaryRetryMessage.class);
        String key = m.tx().userId().toString();

        try {
            salaryService.handleIfSalary(m.tx());   // 급여 스텝만 재실행 (멱등)
            meterRegistry.counter("tx.salary.retry.success").increment();
            log.info("salary_retry_success",
                    kv("event_type", "salary_retry_success"),
                    kv("attempt",    m.attempt()),
                    kv("user_id",    key));
        } catch (Exception e) {
            if (m.attempt() >= MAX_ATTEMPTS) {
                kafkaTemplate.send(DLT_TOPIC, key, objectMapper.writeValueAsString(m));
                meterRegistry.counter("tx.salary.dlt").increment();
                log.error("salary_dlt",
                        kv("event_type", "salary_dlt"),
                        kv("attempt",    m.attempt()),
                        kv("user_id",    key),
                        kv("fail_reason","급여 재처리 " + MAX_ATTEMPTS + "회 실패 → DLT 격리(유실 아님)"),
                        kv("error",      e.getMessage()));
            } else {
                kafkaTemplate.send(RETRY_TOPIC, key, objectMapper.writeValueAsString(m.next()));
                meterRegistry.counter("tx.salary.retry.again").increment();
                log.warn("salary_retry_again",
                        kv("event_type", "salary_retry_again"),
                        kv("attempt",    m.attempt()),
                        kv("user_id",    key),
                        kv("error",      e.getMessage()));
            }
        }
    }
}
