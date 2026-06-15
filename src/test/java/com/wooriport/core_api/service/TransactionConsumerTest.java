package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wooriport.core_api.base.dto.transaction.BatchPersistResult;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    // 실제 파싱을 태우기 위해 spy (mock 아님)
    @Spy ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock TransactionService transactionService;
    @Mock ChallengeService challengeService;
    @Mock SalaryService salaryService;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    // 측정 하네스. 실제 메트릭 동작을 위해 SimpleMeterRegistry 를 spy 로 주입.
    @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @InjectMocks TransactionConsumer consumer;

    private static final String VALID_JSON = """
            {"asset_number":"5429-4494-5284-1827","amount":12500,"category":"식비",
             "sender_name":"스타벅스 코리아","transactionAt":"2026-05-14T12:34:56"}
            """;

    @Test
    @DisplayName("파싱 실패 메시지뿐이면 적재분이 없어 후속 반응을 호출하지 않는다")
    void parseFailure_skipsReactions() {
        given(transactionService.batchPersist(any())).willReturn(new BatchPersistResult(List.of(), 0));

        consumer.consume(List.of("{ not valid json"));

        verifyNoInteractions(challengeService, salaryService);
    }

    @Test
    @DisplayName("적재 결과가 비면(매칭 asset 없음) 후속 반응을 호출하지 않는다")
    void persistEmpty_noReactions() {
        given(transactionService.batchPersist(any())).willReturn(new BatchPersistResult(List.of(), 0));

        consumer.consume(List.of(VALID_JSON));

        verify(transactionService).batchPersist(any());
        verifyNoInteractions(challengeService, salaryService);
    }

    @Test
    @DisplayName("적재 성공 시 챌린지와 급여 처리에 모두 위임한다")
    void persistSuccess_delegatesToBoth() {
        PersistedTransaction tx = sampleTx();
        given(transactionService.batchPersist(any())).willReturn(new BatchPersistResult(List.of(tx), 0));
        given(challengeService.hasActiveChallenge(tx.userId())).willReturn(true);

        consumer.consume(List.of(VALID_JSON));

        verify(challengeService).updateProgress(tx.userId(), tx.category(), tx.senderName(),
                tx.transactionAt(), tx.rawAmount());
        verify(salaryService).handleIfSalary(tx);
    }

    @Test
    @DisplayName("★ 챌린지 증분이 예외로 터지면 DB 재계산으로 복구하고 급여 처리는 그대로 실행된다")
    void challengeThrows_recomputesAndSalaryStillRuns() {
        PersistedTransaction tx = sampleTx();
        given(transactionService.batchPersist(any())).willReturn(new BatchPersistResult(List.of(tx), 0));
        given(challengeService.hasActiveChallenge(tx.userId())).willReturn(true);
        willThrow(new RuntimeException("redis down"))
                .given(challengeService).updateProgress(any(), any(), any(), any(), anyLong());

        consumer.consume(List.of(VALID_JSON));

        verify(challengeService).recomputeFromDb(tx.userId());   // 증분 실패 → DB 재계산 복구
        verify(salaryService).handleIfSalary(tx);                // 챌린지 실패와 무관하게 호출됨
    }

    private PersistedTransaction sampleTx() {
        return new PersistedTransaction(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "식비", "스타벅스 코리아", LocalDateTime.of(2026, 5, 14, 12, 34, 56), 12500L, true);
    }
}
