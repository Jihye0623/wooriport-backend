package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.PortfolioFlows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioFlowRepository extends JpaRepository<PortfolioFlows, UUID> {

    // 대시보드: 이벤트의 모음 통장 잔액 조회용
    @Query("""
        SELECT f FROM PortfolioFlows f
        LEFT JOIN FETCH f.gatheringAsset
        WHERE f.user.id = :userId
          AND f.event.id = :eventId
        """)
    Optional<PortfolioFlows> findByUserIdAndEventId(
            @Param("userId") UUID userId,
            @Param("eventId") UUID eventId);

    // 대시보드 포트폴리오: 사용자의 모든 흐름 (기본 + 이벤트)
    @Query("""
        SELECT DISTINCT f FROM PortfolioFlows f
        LEFT JOIN FETCH f.items
        WHERE f.user.id = :userId
        """)
    List<PortfolioFlows> findAllByUserIdWithItems(@Param("userId") UUID userId);

    // asset-portfolio 화면: 흐름 + gatheringAsset + items + item.asset + item.product 까지
    // 한 번에 페치 (items 컬렉션 하나에만 fetch 적용)
    @Query("""
        SELECT DISTINCT f FROM PortfolioFlows f
        LEFT JOIN FETCH f.gatheringAsset
        LEFT JOIN FETCH f.items i
        LEFT JOIN FETCH i.asset
        LEFT JOIN FETCH i.product
        WHERE f.user.id = :userId
        """)
    List<PortfolioFlows> findAllByUserIdWithDetails(@Param("userId") UUID userId);
}
