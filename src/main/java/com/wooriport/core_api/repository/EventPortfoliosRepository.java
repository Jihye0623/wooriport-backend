package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.EventPortfolios;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventPortfoliosRepository extends JpaRepository<EventPortfolios, UUID> {

    // 이벤트별 포트폴리오 조회
    @Query("""
        SELECT ep FROM EventPortfolios ep
        WHERE ep.event.id = :eventId
        """)
    List<EventPortfolios> findByEventId(@Param("eventId") UUID eventId);
}