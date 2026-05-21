package com.wooriport.core_api.repository;


import com.wooriport.core_api.domain.TransferPlans;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferPlanRepository extends JpaRepository<TransferPlans, UUID> {

    @Query("""
        SELECT tp FROM TransferPlans tp
        WHERE tp.user.id = :userId
          AND tp.year = :year
          AND tp.month = :month
          AND tp.deletedAt IS NULL
        ORDER BY tp.assetType
        """)
    List<TransferPlans> findByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
        SELECT tp FROM TransferPlans tp
        WHERE tp.user.id = :userId
          AND tp.year = :year
          AND tp.month = :month
          AND tp.isConfirmed = true
          AND tp.deletedAt IS NULL
        """)
    List<TransferPlans> findByUserIdAndYearAndMonthAndIsConfirmedTrue(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
        SELECT tp FROM TransferPlans tp
        WHERE tp.id = :id
          AND tp.user.id = :userId
          AND tp.deletedAt IS NULL
        """)
    Optional<TransferPlans> findByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    @Modifying
    @Query("""
        UPDATE TransferPlans tp
        SET tp.deletedAt = CURRENT_TIMESTAMP
        WHERE tp.user.id = :userId
          AND tp.year = :year
          AND tp.month = :month
        """)
    void deleteByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 자동이체 Batch — 오늘이 이체일인 사용자 계획
    @Query("""
        SELECT t FROM TransferPlans t
        WHERE t.user.id = :userId
          AND t.year = :year
          AND t.month = :month
          AND t.isConfirmed = true
          AND t.deletedAt IS NULL
        """)
    List<TransferPlans> findConfirmedPlans(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // TransferPlanRepository.java에 추가
    void deleteByUserIdAndYearAndMonthAndIsConfirmedFalse(
            UUID userId, int year, int month);
}