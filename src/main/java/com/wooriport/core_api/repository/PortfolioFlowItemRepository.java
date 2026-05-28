package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.PortfolioFlowItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioFlowItemRepository extends JpaRepository<PortfolioFlowItems, UUID> {

    // 대시보드 포트폴리오: 사용자 전체 PUT 항목 (상품 투자 항목만)
    // asset / product fetch join 으로 N+1 방지
    @Query("""
        SELECT pi FROM PortfolioFlowItems pi
        LEFT JOIN FETCH pi.asset
        LEFT JOIN FETCH pi.product
        WHERE pi.flow.user.id = :userId
          AND pi.stepType = com.wooriport.core_api.domain.PortfolioFlowItems.StepType.PUT
        """)
    List<PortfolioFlowItems> findAllPutByUserIdWithAsset(@Param("userId") UUID userId);

    // 급여 리밸런싱 3-2: startedAt이 설정된(활성) 흐름의 PUT items 중 asset과 amount가 있는 항목
    @Query("""
        SELECT pi FROM PortfolioFlowItems pi
        LEFT JOIN FETCH pi.asset
        WHERE pi.flow.user.id = :userId
          AND pi.flow.startedAt IS NOT NULL
          AND pi.stepType = com.wooriport.core_api.domain.PortfolioFlowItems.StepType.PUT
          AND pi.asset IS NOT NULL
          AND pi.amount IS NOT NULL
        """)
    List<PortfolioFlowItems> findActiveFlowItemsWithAmountByUserId(@Param("userId") UUID userId);

    // PATCH 시 기존 items 일괄 삭제 후 재생성
    @Modifying
    @Query("DELETE FROM PortfolioFlowItems pi WHERE pi.flow.id = :flowId")
    void deleteByFlowId(@Param("flowId") UUID flowId);
}
