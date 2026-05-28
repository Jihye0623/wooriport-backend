package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Event;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    // 사용자의 활성 이벤트 전체
    @Query("""
        SELECT e FROM Event e
        WHERE e.user.id = :userId
          AND e.deletedAt IS NULL
        ORDER BY e.createdAt DESC
        """)
    List<Event> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    // 가장 최근 활성 이벤트 1개
    @Query("""
        SELECT e FROM Event e
        WHERE e.user.id = :userId
          AND e.deletedAt IS NULL
        ORDER BY e.createdAt DESC
        LIMIT 1
        """)
    Optional<Event> findActiveByUserId(@Param("userId") UUID userId);

    // 단건 조회 (본인 이벤트인지 검증 포함)
    @Query("""
        SELECT e FROM Event e
        WHERE e.id = :eventId
          AND e.user.id = :userId
          AND e.deletedAt IS NULL
        """)
    Optional<Event> findByIdAndUserId(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId);

    // 대시보드에 활성화된 이벤트 전체 조회
    @Query("""
        SELECT e FROM Event e
        WHERE e.user.id = :userId
          AND e.deletedAt IS NULL
        ORDER BY e.deadline ASC
        """)
    List<Event> findActiveDashboardEvents(@Param("userId") UUID userId);
}
