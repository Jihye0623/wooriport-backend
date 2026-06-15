package com.wooriport.core_api.service;

import static net.logstash.logback.argument.StructuredArguments.kv;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.admin.FailedSalaryActionResponseDto;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.base.dto.transaction.SalaryRetryMessage;
import com.wooriport.core_api.domain.FailedSalaryAction;
import com.wooriport.core_api.repository.FailedSalaryActionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 관리자용 급여 DLT 처리 서비스.
 *  - listPending : 격리된 실패 목록 조회
 *  - redrive     : 원인 수정 후 재투입. attempt 를 0 으로 리셋해 retry 토픽으로 다시 보냄(멱등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSalaryDltService {

    private final FailedSalaryActionRepository repository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public List<FailedSalaryActionResponseDto> listPending() {
        return repository.findByStatusOrderByCreatedAtDesc(FailedSalaryAction.Status.PENDING)
                .stream().map(FailedSalaryActionResponseDto::from).toList();
    }

    @Transactional
    public void redrive(UUID id) {
        FailedSalaryAction f = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 DLT 항목이 없습니다: " + id));
        if (f.getStatus() == FailedSalaryAction.Status.RESOLVED) {
            throw new IllegalStateException("이미 처리된 항목입니다: " + id);
        }

        try {
            PersistedTransaction tx = objectMapper.readValue(f.getPayload(), PersistedTransaction.class);
            // attempt 0 으로 리셋 → 원인 수정 후 새로 재시도 기회 부여. handleIfSalary 는 멱등이라 안전.
            String payload = objectMapper.writeValueAsString(new SalaryRetryMessage(tx, 0));
            kafkaTemplate.send(SalaryRetryConsumer.RETRY_TOPIC, tx.userId().toString(), payload);
        } catch (Exception e) {
            throw new RuntimeException("redrive 직렬화 실패: " + e.getMessage(), e);
        }

        f.resolve();
        meterRegistry.counter("tx.salary.dlt.redriven").increment();
        log.info("salary_dlt_redriven",
                kv("event_type", "salary_dlt_redriven"),
                kv("failed_id",  id.toString()),
                kv("user_id",    f.getUserId().toString()));
    }
}
