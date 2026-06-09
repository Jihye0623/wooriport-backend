package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.PortfolioFlows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioFlowRepository extends JpaRepository<PortfolioFlows, UUID> {

    // 대시보드 포트폴리오: 사용자의 모든 흐름
    @Query("""
        SELECT DISTINCT f FROM PortfolioFlows f
        LEFT JOIN FETCH f.items
        WHERE f.user.id = :userId
        """)
    List<PortfolioFlows> findAllByUserIdWithItems(@Param("userId") UUID userId);

    // 이체 계획용: 활성 흐름 + gatheringAsset만 페치
    @Query("""
        SELECT f FROM PortfolioFlows f
        LEFT JOIN FETCH f.gatheringAsset
        WHERE f.user.id = :userId
          AND f.isActive = true
          AND f.gatheringAsset IS NOT NULL
          AND f.amount IS NOT NULL
        """)
    List<PortfolioFlows> findActiveByUserIdWithGatheringAsset(@Param("userId") UUID userId);

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

    // 자산 삭제 가능 여부 판단: 흐름의 '모을 통장'으로 사용 중인지 확인
    boolean existsByGatheringAssetId(UUID gatheringAssetId);
}
