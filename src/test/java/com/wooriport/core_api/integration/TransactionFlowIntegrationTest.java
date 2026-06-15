package com.wooriport.core_api.integration;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.MiniChallenges;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import com.wooriport.core_api.repository.AssetRepository;
import com.wooriport.core_api.repository.TransactionRepository;
import com.wooriport.core_api.repository.UserRepository;
import com.wooriport.core_api.service.ChallengeRedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * L3 통합테스트 — transaction-events 발행이 실제로 컨슈머를 거쳐
 *  ① DB 적재(음수)  ② 미니챌린지 Redis 카운트 까지 흐르는지 end-to-end 검증.
 * EmbeddedKafka(in-JVM) + Testcontainers(PostgreSQL, Redis)로 외부 인프라 없이 구동한다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"transaction-events"})
@Testcontainers
class TransactionFlowIntegrationTest {

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
        // EmbeddedKafka 브로커 주소를 앱 컨슈머/프로듀서에 주입
        r.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        r.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        r.add("spring.kafka.producer.value-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired ChallengeRedisService challengeRedisService;

    @Test
    @DisplayName("transaction-events 발행 → DB에 음수 거래로 적재된다")
    void publishedTransaction_isPersistedAsNegative() {
        Users user = userRepository.save(Users.builder()
                .password("pw").email("persist@test.com").name("홍길동").build());
        Assets asset = assetRepository.save(creditCard(user, "5429-4494-5284-1827"));

        kafkaTemplate.send("transaction-events", asset.getAssetNumber(),
                payload(asset.getAssetNumber(), 12500, "식비"));

        UUID assetId = asset.getId();
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Transactions tx = transactionRepository.findAll().stream()
                    .filter(t -> t.getAsset().getId().equals(assetId))
                    .findFirst().orElse(null);
            assertThat(tx).isNotNull();
            assertThat(tx.getAmount()).isEqualTo(-12500L);   // CREDIT_CARD 출금 → 음수
            assertThat(tx.getCategory()).isEqualTo("식비");
        });
    }

    @Test
    @DisplayName("transaction-events 발행 → 매칭 카테고리 미니챌린지의 Redis 카운트가 증가한다")
    void publishedTransaction_incrementsChallengeRedisCount() {
        Users user = userRepository.save(Users.builder()
                .password("pw").email("challenge@test.com").name("김철수").build());
        Assets asset = assetRepository.save(creditCard(user, "1111-2222-3333-4444"));

        // 진행 중인 "식비" 금액(AMOUNT) 챌린지(한도 충분히 큼)를 Redis에 등록.
        // 거래(식비·12:34 점심시간)가 매칭되도록 서브타입은 LUNCH(식비 11~14시).
        MiniChallenges challenge = MiniChallenges.builder()
                .id(UUID.randomUUID()).user(user).title("식비 절약").category("식비")
                .challengeType(MiniChallenges.ChallengeType.AMOUNT)
                .challengeSubType(MiniChallenges.ChallengeSubType.LUNCH)
                .target(10_000_000L)
                .status(MiniChallenges.ChallengeStatus.IN_PROGRESS)
                .build();
        challengeRedisService.save(user.getId(), challenge);

        kafkaTemplate.send("transaction-events", asset.getAssetNumber(),
                payload(asset.getAssetNumber(), 12500, "식비"));

        UUID userId = user.getId();
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<Object, Object> cache = challengeRedisService.get(userId);
            assertThat(cache.get("currentValue")).isEqualTo("12500");  // rawAmount 누적
        });
    }

    private Assets creditCard(Users user, String assetNumber) {
        return Assets.builder()
                .user(user)
                .institution("우리카드")
                .assetNumber(assetNumber)
                .assetType(Assets.AccountType.CREDIT_CARD)
                .bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now())
                .build();
    }

    private String payload(String assetNumber, long amount, String category) {
        return """
                {"asset_number":"%s","amount":%d,"category":"%s","sender_name":"스타벅스 코리아","transactionAt":"2026-05-14T12:34:56"}
                """.formatted(assetNumber, amount, category);
    }
}
