package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.PortfolioItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItems, UUID> {

    // 기본 포트폴리오 (event_id = null, 최신순)
    @Query("""
        SELECT p FROM PortfolioItems p
        WHERE p.user.id = :userId
          AND p.event IS NULL
        ORDER BY p.createdAt DESC
        """)
    List<PortfolioItems> findByUserIdAndEventIsNullOrderByCreatedAtDesc(
            @Param("userId") UUID userId);

    // 이벤트 포트폴리오
    @Query("""
        SELECT p FROM PortfolioItems p
        WHERE p.user.id = :userId
          AND p.event.id = :eventId
        """)
    List<PortfolioItems> findByUserIdAndEventId(
            @Param("userId") UUID userId,
            @Param("eventId") UUID eventId);

    // 기본 포트폴리오 삭제 (event_id = null)
    @Modifying
    @Query("""
    DELETE FROM PortfolioItems p
    WHERE p.user.id = :userId
      AND p.event IS NULL
    """)
    void deleteByUserIdAndEventIsNull(@Param("userId") UUID userId);

    // 이벤트 포트폴리오 삭제 (수정 시 재생성)
    void deleteByUserIdAndEventId(UUID userId, UUID eventId);
}