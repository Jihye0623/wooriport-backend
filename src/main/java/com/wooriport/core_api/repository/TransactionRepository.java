package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transactions, UUID> {

    // ──────────────────────────────────────
    // 이상 소비 감지용
    // ──────────────────────────────────────

    // 특정 연월 + 카테고리별 지출 합계
    @Query("""
        SELECT COALESCE(SUM(ABS(t.amount)), 0)
        FROM Transactions t
        WHERE t.user.id = :userId
          AND t.category = :category
          AND t.amount < 0
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        """)
    Long sumExpenseByCategory(
            @Param("userId") UUID userId,
            @Param("category") String category,
            @Param("year") int year,
            @Param("month") int month);

    // ──────────────────────────────────────
    // 월간 리포트 집계용
    // ──────────────────────────────────────

    // 월별 총 수입
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transactions t
        WHERE t.user.id = :userId
          AND t.amount > 0
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        """)
    Long sumIncomeByMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 월별 총 지출
    @Query("""
        SELECT COALESCE(SUM(ABS(t.amount)), 0)
        FROM Transactions t
        WHERE t.user.id = :userId
          AND t.amount < 0
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        """)
    Long sumExpenseByMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
    SELECT t FROM Transactions t
    WHERE t.user.id = :userId
      AND t.amount > 0
      AND (
          t.category LIKE '%급여%'
          OR t.category LIKE '%월급%'
          OR t.category LIKE '%임금%'
          OR t.category LIKE '%salary%'
      )
    ORDER BY t.transactionAt DESC
    """)
    List<Transactions> findSalaryTransactionsByUserId(@Param("userId") UUID userId);
    // 가장 최근 급여 트랜잭션 1건 (RebalancingTasklet, generate에서 사용)
    @Query("""
        SELECT t FROM Transactions t
        WHERE t.user.id = :userId
          AND t.amount > 0
          AND (
              t.category LIKE '%급여%'
              OR t.category LIKE '%월급%'
              OR t.category LIKE '%임금%'
              OR t.category LIKE '%salary%'
          )
        ORDER BY t.transactionAt DESC
        LIMIT 1
        """)
    Optional<Transactions> findLatestSalaryTransaction(@Param("userId") UUID userId);

    @Query("""
        SELECT t FROM Transactions t
        WHERE t.asset.id = :assetId
          AND t.amount > 0
          AND (
              t.category LIKE '%급여%'
              OR t.category LIKE '%월급%'
              OR t.category LIKE '%임금%'
              OR t.category LIKE '%salary%'
          )
        ORDER BY t.transactionAt DESC
        LIMIT 1
        """)
    Optional<Transactions> findLatestSalaryTransactionByAssetId(@Param("assetId") UUID assetId);

    @Query("""
    SELECT t.category, COALESCE(SUM(ABS(t.amount)) / 3, 0) AS monthlyAvg
    FROM Transactions t
    WHERE t.user.id = :userId
      AND t.amount < 0
      AND t.transactionAt >= :threeMonthsAgo
    GROUP BY t.category
    ORDER BY monthlyAvg DESC
    """)
    List<Object[]> findCategoryExpenseAvg(
            @Param("userId") UUID userId,
            @Param("threeMonthsAgo") LocalDateTime threeMonthsAgo);

    // 대시보드: 이번 달 카테고리별 지출 합계 [category, sumAmount]
    @Query("""
        SELECT t.category, COALESCE(SUM(ABS(t.amount)), 0)
        FROM Transactions t
        WHERE t.user.id = :userId
          AND t.amount < 0
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        GROUP BY t.category
        """)
    List<Object[]> sumExpenseGroupByCategory(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 대시보드 sub 필드용: 이번 달 지출 거래 (카테고리/가맹점 그룹핑은 서비스에서)
    @Query("""
        SELECT t FROM Transactions t
        WHERE t.user.id = :userId
          AND t.amount < 0
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        """)
    List<Transactions> findMonthlyExpenses(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 월간 리포트용: 입출금 전체 거래 (asset JOIN FETCH)
    @Query("""
        SELECT t FROM Transactions t
        JOIN FETCH t.asset
        WHERE t.user.id = :userId
          AND EXTRACT(YEAR FROM t.transactionAt) = :year
          AND EXTRACT(MONTH FROM t.transactionAt) = :month
        ORDER BY t.transactionAt ASC
        """)
    List<Transactions> findAllByMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

}