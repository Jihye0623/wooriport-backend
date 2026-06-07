package com.wooriport.core_api.integration;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wooriport.core_api.base.dto.transaction.PersistedTransaction;
import com.wooriport.core_api.domain.FailedSalaryAction;
import com.wooriport.core_api.repository.*;
import com.wooriport.core_api.service.AdminSalaryDltService;
import com.wooriport.core_api.service.ChallengeRedisService;
import com.wooriport.core_api.service.FaultInjector;
import com.wooriport.core_api.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.willThrow;

/**
 * Phase 1 (F-2) 결함 시나리오 통합테스트 — 결함을 주입하고 시스템이 정확히 복구함을 end-to-end 증명.
 * EmbeddedKafka + Testcontainers(PostgreSQL, Redis). FaultInjector 는 @MockBean 으로 시나리오별 제어.
 *
 *  D1 멱등 적재   : 같은 event_id 재전송 → 행 1개만 (중복 0)
 *  C1 챌린지 재계산: 증분 1회 실패 → DB 재계산으로 currentValue 정확 보정
 *  S1 급여 재처리  : 급여 처리 1회 실패 → 재처리 토픽 → 재실행 성공(이체계획 생성)
 *  S2 급여 DLT     : 급여 처리 영구 실패 → 3회 재시도 후 DLT 격리(유실 아님)
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        "transaction-events",
        "transaction-events.salary.retry",
        "transaction-events.salary.DLT"
})
@Testcontainers
class KafkaResilienceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    // FaultInjector 를 mock 으로 대체해 시나리오별 결함을 정밀 주입 (기본은 no-op)
    @MockitoBean FaultInjector faultInjector;
    // 급여 성공 경로의 알림 부수효과 차단
    @MockitoBean NotificationService notificationService;

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired MiniChallengesRepository miniChallengesRepository;
    @Autowired PortfolioRepository portfolioRepository;
    @Autowired TransferPlanRepository transferPlanRepository;
    @Autowired ChallengeRedisService challengeRedisService;
    @Autowired MeterRegistry meterRegistry;
    @Autowired FailedSalaryActionRepository failedSalaryActionRepository;
    @Autowired AdminSalaryDltService adminSalaryDltService;
    @Autowired ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // D1 — 멱등 적재: 같은 event_id 2번 → 행 1개만
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("D1 같은 event_id 가 두 번 와도 거래는 1건만 적재된다 (멱등)")
    void duplicateEventId_persistsOnce() {
        Users user = userRepository.save(user("d1@test.com", "중복테스터"));
        Assets card = assetRepository.save(creditCard(user, "1000-0000-0000-0001"));
        String eventId = "evt-dup-" + UUID.randomUUID();
        double dupBefore = counter("tx.duplicate.detected");

        // 동일 event_id 2회 발행 (같은 key → 같은 파티션 → 순서 보장)
        kafkaTemplate.send("transaction-events", card.getAssetNumber(),
                payload(eventId, card.getAssetNumber(), 12500, "식비"));
        kafkaTemplate.send("transaction-events", card.getAssetNumber(),
                payload(eventId, card.getAssetNumber(), 12500, "식비"));

        UUID assetId = card.getId();
        // 두 번째가 중복으로 감지될 때까지 대기
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> counter("tx.duplicate.detected") - dupBefore >= 1);

        long rows = transactionRepository.findAll().stream()
                .filter(t -> t.getAsset().getId().equals(assetId)).count();
        assertThat(rows).isEqualTo(1);   // 중복 적재 0
    }

    // ─────────────────────────────────────────────────────────────
    // C1 — 챌린지 재계산: 증분 1회 실패 → DB 재계산으로 정확 보정
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("C1 챌린지 증분이 실패하면 DB 재계산으로 currentValue 가 정확히 보정된다")
    void challengeIncrementFails_recomputedFromDb() {
        // 증분 1회 실패 후 정상 (재계산 경로는 maybeFailChallenge 를 호출하지 않음)
        willThrow(new RuntimeException("[chaos] challenge fail once"))
                .willDoNothing()
                .given(faultInjector).maybeFailChallenge();

        Users user = userRepository.save(user("c1@test.com", "재계산테스터"));
        Assets card = assetRepository.save(creditCard(user, "1000-0000-0000-0002"));

        // LUNCH(식비 11~14시) 진행중 챌린지. startedAt 을 과거로 둬 거래가 윈도우에 포함되게.
        MiniChallenges challenge = miniChallengesRepository.save(MiniChallenges.builder()
                .user(user).title("점심 절약").category("식비")
                .challengeType(MiniChallenges.ChallengeType.AMOUNT)
                .challengeSubType(MiniChallenges.ChallengeSubType.LUNCH)
                .target(10_000_000L)
                .status(MiniChallenges.ChallengeStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build());
        challengeRedisService.save(user.getId(), challenge);

        // 식비·12:34 거래 → LUNCH 매칭, 금액 12500
        kafkaTemplate.send("transaction-events", card.getAssetNumber(),
                payload("evt-c1-" + UUID.randomUUID(), card.getAssetNumber(), 12500, "식비"));

        UUID userId = user.getId();
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            // 증분은 실패했지만 DB 재계산으로 currentValue 가 12500 으로 보정됨
            assertThat(challengeRedisService.get(userId).get("currentValue")).isEqualTo("12500");
        });
    }

    // ─────────────────────────────────────────────────────────────
    // S1 — 급여 재처리 성공: 1회 실패 → 재처리 토픽 → 재실행 성공(이체계획 생성)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("S1 급여 처리가 1회 실패해도 재처리 토픽을 거쳐 이체계획이 생성된다")
    void salaryFailsOnce_retriedAndSucceeds() {
        // notificationService 는 @MockBean 이라 void 메서드 기본 no-op (부수효과 차단)
        // 급여 처리 1회 실패 후 성공
        willThrow(new RuntimeException("[chaos] salary fail once"))
                .willDoNothing()
                .given(faultInjector).maybeFailSalary();

        Users user = userRepository.save(userWithSalary("s1@test.com", "급여성공", 3_000_000L));
        Assets autoAsset = assetRepository.save(wooriAccount(user, "2000-0000-0000-0001")); // 자동이체 출발 계좌
        Assets target = assetRepository.save(wooriAccount(user, "2000-0000-0000-0002"));     // 분배 대상
        user.connectAutoTransfer(autoAsset.getId());
        userRepository.save(user);

        // generateFromSalary 가 읽을 양수 급여 거래 + 포트폴리오 시드
        transactionRepository.save(Transactions.builder()
                .user(user).asset(autoAsset).amount(3_000_000L).category("급여")
                .senderName("회사").transactionAt(LocalDateTime.now()).build());
        portfolioRepository.save(Portfolios.builder()
                .user(user).asset(target).assetAmount(500_000L).build());

        // 급여 이벤트(양수, 급여, 자동이체 계좌)
        kafkaTemplate.send("transaction-events", autoAsset.getAssetNumber(),
                payload("evt-s1-" + UUID.randomUUID(), autoAsset.getAssetNumber(), 3_000_000, "급여"));

        UUID userId = user.getId();
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(transferPlanRepository.findByUserIdAndYearAndMonth(userId, year, month))
                        .isNotEmpty());   // 재처리로 이체계획 생성 성공
    }

    // ─────────────────────────────────────────────────────────────
    // S2 — 급여 영구 실패 → 3회 재시도 후 DLT 격리(유실 아님)
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("S2 급여 처리가 계속 실패하면 재시도 끝에 DLT 로 격리된다 (유실 아님)")
    void salaryFailsAlways_endsInDlt() {
        willThrow(new RuntimeException("[chaos] salary fail always"))
                .given(faultInjector).maybeFailSalary();

        Users user = userRepository.save(user("s2@test.com", "급여DLT"));
        Assets autoAsset = assetRepository.save(wooriAccount(user, "2000-0000-0000-0003"));
        user.connectAutoTransfer(autoAsset.getId());
        userRepository.save(user);

        double dltBefore = counter("tx.salary.dlt");
        UUID userId = user.getId();

        kafkaTemplate.send("transaction-events", autoAsset.getAssetNumber(),
                payload("evt-s2-" + UUID.randomUUID(), autoAsset.getAssetNumber(), 3_000_000, "급여"));

        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> counter("tx.salary.dlt") - dltBefore >= 1);   // DLT 격리 확인
        // DLT 컨슈머가 failed_salary_actions 에 감사기록으로 적재(관리자 대시보드용)
        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(failedSalaryActionRepository
                        .findByStatusOrderByCreatedAtDesc(FailedSalaryAction.Status.PENDING)
                        .stream().anyMatch(f -> f.getUserId().equals(userId))).isTrue());
    }

    // ─────────────────────────────────────────────────────────────
    // S3 — 관리자 redrive: DLT 격리 항목을 attempt 0 으로 재투입 + RESOLVED
    // ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("S3 관리자 redrive 는 DLT 항목을 attempt 0 으로 재투입하고 RESOLVED 로 표시한다")
    void adminRedrive_resetsToZeroAndResolves() throws Exception {
        UUID userId = UUID.randomUUID();
        PersistedTransaction tx = new PersistedTransaction(userId, UUID.randomUUID(), UUID.randomUUID(),
                "급여", "회사", LocalDateTime.now(), 3_000_000L, true);
        FailedSalaryAction f = failedSalaryActionRepository.save(FailedSalaryAction.builder()
                .userId(userId).payload(objectMapper.writeValueAsString(tx)).attempts(3)
                .lastError("테스트 격리").status(FailedSalaryAction.Status.PENDING).build());

        double before = counter("tx.salary.dlt.redriven");
        adminSalaryDltService.redrive(f.getId());   // retry 토픽으로 attempt 0 재발행 + RESOLVED

        assertThat(failedSalaryActionRepository.findById(f.getId()).orElseThrow().getStatus())
                .isEqualTo(FailedSalaryAction.Status.RESOLVED);
        assertThat(counter("tx.salary.dlt.redriven") - before).isEqualTo(1.0);
    }

    // ─────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────
    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c != null ? c.count() : 0.0;
    }

    private Users user(String email, String name) {
        return Users.builder().password("pw").email(email).name(name).build();
    }

    private Users userWithSalary(String email, String name, long salary) {
        return Users.builder().password("pw").email(email).name(name).salary(salary).build();
    }

    private Assets creditCard(Users user, String number) {
        return Assets.builder().user(user).institution("우리카드").assetNumber(number)
                .assetType(Assets.AccountType.CREDIT_CARD).bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now()).build();
    }

    private Assets wooriAccount(Users user, String number) {
        return Assets.builder().user(user).institution("우리은행").assetNumber(number)
                .assetType(Assets.AccountType.DEPOSIT).bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now()).build();
    }

    private String payload(String eventId, String assetNumber, long amount, String category) {
        return """
                {"event_id":"%s","asset_number":"%s","amount":%d,"category":"%s",
                 "sender_name":"테스트가맹점","transactionAt":"2026-05-14T12:34:56","producedAt":%d}
                """.formatted(eventId, assetNumber, amount, category, System.currentTimeMillis());
    }
}
