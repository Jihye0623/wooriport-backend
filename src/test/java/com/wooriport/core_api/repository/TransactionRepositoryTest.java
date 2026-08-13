package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Assets;
import com.wooriport.core_api.domain.Transactions;
import com.wooriport.core_api.domain.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * TransactionRepository의 @Query 메서드 검증.
 * EXTRACT(YEAR/MONTH ...) 등 PostgreSQL 전용 함수를 쓰므로 H2가 아닌 실제 Postgres(Testcontainers)로 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;

    @Test
    @DisplayName("sumExpenseByCategory: 해당 연월 + 카테고리의 지출(음수)만 절대값으로 합산한다")
    void sumExpenseByCategory_sumsOnlyMatchingCategoryAndMonth() {
        Users user = saveUser("cat@test.com");
        Assets asset = saveAsset(user);

        saveTx(user, asset, -10_000L, "식비", at(2026, 5, 10));
        saveTx(user, asset, -5_000L, "식비", at(2026, 5, 20));
        saveTx(user, asset, -3_000L, "교통", at(2026, 5, 15));   // 다른 카테고리 — 제외
        saveTx(user, asset, -7_000L, "식비", at(2026, 6, 1));    // 다른 달 — 제외
        saveTx(user, asset, 50_000L, "식비", at(2026, 5, 5));    // 입금(양수) — 제외

        Long result = transactionRepository.sumExpenseByCategory(user.getId(), "식비", 2026, 5);

        assertThat(result).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("sumIncomeByMonth: 해당 연월의 입금(양수)만 합산한다")
    void sumIncomeByMonth_sumsOnlyPositiveAmountsInMonth() {
        Users user = saveUser("income@test.com");
        Assets asset = saveAsset(user);

        saveTx(user, asset, 3_000_000L, "급여", at(2026, 5, 25));
        saveTx(user, asset, 10_000L, "용돈", at(2026, 5, 26));
        saveTx(user, asset, -20_000L, "식비", at(2026, 5, 27));   // 지출 — 제외
        saveTx(user, asset, 100_000L, "용돈", at(2026, 4, 30));   // 다른 달 — 제외

        Long result = transactionRepository.sumIncomeByMonth(user.getId(), 2026, 5);

        assertThat(result).isEqualTo(3_010_000L);
    }

    @Test
    @DisplayName("sumExpenseByMonth: 해당 연월의 지출(음수)만 절대값으로 합산한다")
    void sumExpenseByMonth_sumsOnlyNegativeAmountsInMonth() {
        Users user = saveUser("expense@test.com");
        Assets asset = saveAsset(user);

        saveTx(user, asset, -20_000L, "식비", at(2026, 5, 1));
        saveTx(user, asset, -30_000L, "쇼핑", at(2026, 5, 2));
        saveTx(user, asset, 1_000_000L, "급여", at(2026, 5, 3));  // 입금 — 제외

        Long result = transactionRepository.sumExpenseByMonth(user.getId(), 2026, 5);

        assertThat(result).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("findLatestSalaryTransaction: 급여성 입금 중 가장 최근(현재 시각 이전) 1건만 반환한다")
    void findLatestSalaryTransaction_returnsMostRecentPastOne() {
        Users user = saveUser("salary@test.com");
        Assets asset = saveAsset(user);

        saveTx(user, asset, 2_900_000L, "급여", LocalDateTime.now().minusMonths(2));
        saveTx(user, asset, 3_000_000L, "월급", LocalDateTime.now().minusMonths(1));
        saveTx(user, asset, -50_000L, "식비", LocalDateTime.now()); // 급여 아님 — 제외
        saveTx(user, asset, 9_999_999L, "월급", LocalDateTime.now().plusMonths(1)); // 미래 — 제외

        Optional<Transactions> result = transactionRepository.findLatestSalaryTransaction(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("sumTaxBenefitContributionByMonth: 세제혜택 계좌 타입(ISA/IRP/PENSION_SAVINGS)별로 해당 연월 납입액을 그룹핑한다")
    void sumTaxBenefitContributionByMonth_groupsByAssetType() {
        Users user = saveUser("tax@test.com");
        Assets isaAsset = saveAsset(user, Assets.AccountType.ISA);
        Assets irpAsset = saveAsset(user, Assets.AccountType.IRP);
        Assets checkingAsset = saveAsset(user, Assets.AccountType.CHECKING);

        saveTx(user, isaAsset, 500_000L, "이체", at(2026, 5, 1));
        saveTx(user, isaAsset, 300_000L, "이체", at(2026, 5, 2));
        saveTx(user, irpAsset, 200_000L, "이체", at(2026, 5, 3));
        saveTx(user, checkingAsset, 1_000_000L, "급여", at(2026, 5, 4)); // 세제혜택 계좌 아님 — 제외
        saveTx(user, isaAsset, 100_000L, "이체", at(2026, 4, 30)); // 다른 달 — 제외

        List<Object[]> result = transactionRepository.sumTaxBenefitContributionByMonth(user.getId(), 2026, 5);

        assertThat(result).extracting(row -> tuple(row[0], row[1]))
                .containsExactlyInAnyOrder(
                        tuple(Assets.AccountType.ISA, 800_000L),
                        tuple(Assets.AccountType.IRP, 200_000L));
    }

    private Users saveUser(String email) {
        return userRepository.save(Users.builder()
                .password("pw").email(email).name("홍길동").build());
    }

    private Assets saveAsset(Users user) {
        return saveAsset(user, Assets.AccountType.CHECKING);
    }

    private Assets saveAsset(Users user, Assets.AccountType type) {
        return assetRepository.save(Assets.builder()
                .user(user).institution("우리은행")
                .assetType(type).bankType(Assets.BankType.WOORI)
                .syncedAt(LocalDateTime.now()).build());
    }

    private void saveTx(Users user, Assets asset, long amount, String category, LocalDateTime transactionAt) {
        transactionRepository.save(Transactions.builder()
                .eventId(UUID.randomUUID().toString())
                .user(user).asset(asset)
                .amount(amount).category(category)
                .senderName("테스트").transactionAt(transactionAt)
                .build());
    }

    private LocalDateTime at(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 12, 0);
    }
}
