package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.TransferExecutions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransferExecutionRepository extends JpaRepository<TransferExecutions, UUID> {

    @Query("""
        SELECT te FROM TransferExecutions te
        WHERE te.user.id = :userId
          AND EXTRACT(YEAR FROM te.createdAt) = :year
          AND EXTRACT(MONTH FROM te.createdAt) = :month
        ORDER BY te.createdAt DESC
    """)
    List<TransferExecutions> findByUserIdAndYearAndMonth(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month);

    // 이체 이력 전체 조회 (최신순)
    @Query("""
        SELECT e FROM TransferExecutions e
        WHERE e.user.id = :userId
        ORDER BY e.createdAt DESC
        """)
    List<TransferExecutions> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);
}
