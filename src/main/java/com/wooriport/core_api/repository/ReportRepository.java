package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Reports;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Reports, UUID> {

    // 목록 조회 — 최신순
    @Query("""
        SELECT r FROM Reports r
        WHERE r.user.id = :userId
        ORDER BY r.year DESC, r.month DESC
        """)
    List<Reports> findByUserIdOrderByYearDescMonthDesc(@Param("userId") UUID userId);

    // 특정 월 단건 조회
    @Query("""
        SELECT r FROM Reports r
        WHERE r.user.id = :userId
          AND r.year = :year
          AND r.month = :month
        """)
    Optional<Reports> findByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 중복 생성 방지
    @Query("""
        SELECT COUNT(r) > 0 FROM Reports r
        WHERE r.user.id = :userId
          AND r.year = :year
          AND r.month = :month
        """)
    boolean existsByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);
}