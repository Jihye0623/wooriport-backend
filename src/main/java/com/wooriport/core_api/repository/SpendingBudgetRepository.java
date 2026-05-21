package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.SpendingBudgets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpendingBudgetRepository extends JpaRepository<SpendingBudgets, UUID> {

    // 특정 월 예산 조회 — 리포트 상세에서 예산 vs 실제 비교 시 사용
    @Query("""
        SELECT s FROM SpendingBudgets s
        WHERE s.user.id = :userId
          AND s.year = :year
          AND s.month = :month
        ORDER BY s.amount DESC
        """)
    List<SpendingBudgets> findByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 목표별 예산 조회 — 특정 goal에 연결된 예산 확인 시
    @Query("""
        SELECT s FROM SpendingBudgets s
        WHERE s.event.id = :eventId
          AND s.year = :year
          AND s.month = :month
        """)
    List<SpendingBudgets> findByEventIdAndYearAndMonth(
            @Param("eventId") UUID eventId,
            @Param("year") int year,
            @Param("month") int month);
}