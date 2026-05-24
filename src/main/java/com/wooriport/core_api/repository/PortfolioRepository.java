package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Portfolios;
import com.wooriport.core_api.domain.common.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolios, UUID> {

    // 사용자의 전체 포트폴리오 항목 조회
    // (asset_type 별로 여러 행 반환)
    @Query("""
        SELECT p FROM Portfolios p
        WHERE p.user.id = :userId
        ORDER BY p.createdAt DESC
        """)
    List<Portfolios> findByUserId(@Param("userId") UUID userId);

    // 특정 asset_type 조회
    @Query("""
        SELECT p FROM Portfolios p
        WHERE p.user.id = :userId
          AND p.assetType = :assetType
        ORDER BY p.createdAt DESC
        """)
    List<Portfolios> findByUserIdAndAssetType(
            @Param("userId") UUID userId,
            @Param("assetType") AssetCategory assetType);

    // 포트폴리오 전체 삭제 (재설정 시)
    void deleteByUserId(UUID userId);
}
