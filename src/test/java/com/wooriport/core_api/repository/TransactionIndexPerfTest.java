package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Users;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * idx_transactions_user_date(user_id, transaction_at) 복합 인덱스가 user_id 단일 컬럼 인덱스보다
 * 실제로 더 나은지 확인하는 일회성 성능 스파이크 테스트.
 *
 * 같은 조회 쿼리("특정 유저의 특정 기간 거래 조회, 날짜순 정렬")를 두고 인덱스 구성만 바꿔가며 비교한다:
 *   시나리오 A — user_id 단일 컬럼 인덱스만 있을 때
 *   시나리오 B — (user_id, transaction_at) 복합 인덱스로 교체했을 때
 * 두 시나리오의 EXPLAIN ANALYZE 결과(스캔 방식, 추가 Sort 노드 유무, 비용/실행시간)를 나란히 비교한다.
 *
 * 상시 CI용이 아님 — 15,000건 시드 데이터를 매번 새로 만들어 다소 시간이 걸리고,
 * 결과는 pass/fail보다 콘솔에 출력되는 실행계획 자체가 확인 목적이라 기본은 비활성화해둔다.
 * 확인하려면 아래 @Disabled를 지우고 단독 실행: ./gradlew test --tests "*TransactionIndexPerfTest*"
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Disabled("성능 확인용 스파이크 테스트 — 필요할 때 수동으로 비활성화 해제 후 단독 실행")
class TransactionIndexPerfTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private static final int USER_COUNT = 100;
    private static final int TX_PER_USER = 150; // 총 15,000건
    private static final String[] CATEGORIES = {"식비", "교통", "쇼핑", "의료", "여가"};

    @Test
    void compareSingleColumnIndex_vs_compositeIndex_forDateRangeQuery() {
        UUID targetUserId = seedData();

        String query = """
                EXPLAIN ANALYZE
                SELECT * FROM transactions
                WHERE user_id = ?
                  AND transaction_at >= '2026-06-01 00:00:00'
                  AND transaction_at <  '2026-07-01 00:00:00'
                ORDER BY transaction_at ASC
                """;

        // 엔티티 @Table(indexes=...)로 기동 시 이미 생성된 복합 인덱스 2개를 걷어내고,
        // user_id 단일 컬럼 인덱스만 남긴 상태를 시나리오 A로 재현한다.
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_transactions_user_date");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_transactions_category");
        jdbcTemplate.execute("CREATE INDEX idx_perf_user_only ON transactions (user_id)");
        jdbcTemplate.execute("ANALYZE transactions");
        List<String> singleColumnPlan = jdbcTemplate.queryForList(query, String.class, targetUserId);

        // 시나리오 B — 단일 컬럼 인덱스를 걷어내고 (user_id, transaction_at) 복합 인덱스로 교체
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_perf_user_only");
        jdbcTemplate.execute("CREATE INDEX idx_perf_user_date_composite ON transactions (user_id, transaction_at)");
        jdbcTemplate.execute("ANALYZE transactions");
        List<String> compositePlan = jdbcTemplate.queryForList(query, String.class, targetUserId);

        printPlan("[시나리오 A] user_id 단일 컬럼 인덱스만 있을 때", singleColumnPlan);
        printPlan("[시나리오 B] (user_id, transaction_at) 복합 인덱스로 교체", compositePlan);
    }

    private void printPlan(String label, List<String> planLines) {
        System.out.println("\n===== " + label + " =====");
        planLines.forEach(System.out::println);
    }

    /** 유저 100명 × 계좌 1개 × 거래 150건(6개월치, 카테고리 랜덤) = 총 15,000건 시드 */
    private UUID seedData() {
        List<UUID> userIds = new ArrayList<>();
        List<UUID> assetIds = new ArrayList<>();

        for (int i = 0; i < USER_COUNT; i++) {
            Users user = userRepository.save(Users.builder()
                    .password("pw").email("perf-user-" + i + "@test.com").name("유저" + i).build());
            Assets asset = assetRepository.save(Assets.builder()
                    .user(user).institution("우리은행")
                    .assetType(Assets.AccountType.CHECKING).bankType(Assets.BankType.WOORI)
                    .syncedAt(LocalDateTime.now()).build());
            userIds.add(user.getId());
            assetIds.add(asset.getId());
        }
        entityManager.flush(); // 아래 raw JDBC INSERT가 참조할 users/assets 행을 먼저 실제 DB로 내보냄

        String insertSql = """
                INSERT INTO transactions (id, user_id, asset_id, amount, category, sender_name, transaction_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        List<Object[]> batchArgs = new ArrayList<>();
        for (int u = 0; u < USER_COUNT; u++) {
            for (int t = 0; t < TX_PER_USER; t++) {
                int monthOffset = t % 6; // 최근 6개월에 고르게 분산
                LocalDateTime txAt = LocalDateTime.of(2026, 6, 1, 12, 0).minusMonths(monthOffset).plusDays(t % 27);
                String category = CATEGORIES[t % CATEGORIES.length];
                long amount = -(1_000L + (t % 50) * 1_000L); // 지출 위주

                batchArgs.add(new Object[]{
                        UUID.randomUUID(), userIds.get(u), assetIds.get(u),
                        amount, category, "테스트가맹점", txAt
                });
            }
        }
        jdbcTemplate.batchUpdate(insertSql, batchArgs);

        return userIds.get(0);
    }
}
