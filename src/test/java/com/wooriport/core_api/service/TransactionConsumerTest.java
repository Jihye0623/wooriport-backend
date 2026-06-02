package com.wooriport.core_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @InjectMocks TransactionConsumer consumer;

    private static final String VALID_JSON = """
            {"asset_number":"5429-4494-5284-1827","amount":12500,"category":"식비",
             "sender_name":"스타벅스 코리아","transactionAt":"2026-05-14T12:34:56"}
            """;

    @Test
    @DisplayName("메시지 파싱 실패 시 어떤 위임도 하지 않는다")
    void parseFailure_skipsEverything() {
        consumer.consume("{ not valid json");

        verifyNoInteractions(transactionService, challengeService, salaryService);
    }

    @Test
    @DisplayName("적재 결과가 null(매칭 asset 없음)이면 후속 반응을 호출하지 않는다")
    void persistNull_noReactions() {
        given(transactionService.persist(any())).willReturn(null);

        consumer.consume(VALID_JSON);

        verify(transactionService).persist(any());
        verifyNoInteractions(challengeService, salaryService);
    }

    @Test
    @DisplayName("적재 성공 시 챌린지와 급여 처리에 모두 위임한다")
    void persistSuccess_delegatesToBoth() {
        PersistedTransaction tx = sampleTx();
        given(transactionService.persist(any())).willReturn(tx);

        consumer.consume(VALID_JSON);

        verify(challengeService).updateProgress(tx.userId(), tx.category(), tx.rawAmount());
        verify(salaryService).handleIfSalary(tx);
    }

    @Test
    @DisplayName("★ 챌린지 처리가 예외로 터져도 급여 처리는 실행된다 (best-effort 격리)")
    void challengeThrows_salaryStillRuns() {
        PersistedTransaction tx = sampleTx();
        given(transactionService.persist(any())).willReturn(tx);
        willThrow(new RuntimeException("redis down"))
                .given(challengeService).updateProgress(any(), any(), anyLong());

        consumer.consume(VALID_JSON);

        verify(salaryService).handleIfSalary(tx); // 챌린지 실패와 무관하게 호출됨
    }

    private PersistedTransaction sampleTx() {
        return new PersistedTransaction(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "식비", 12500L, true);
    }
}
