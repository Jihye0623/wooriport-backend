package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Assets;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Assets, UUID> {
    // 급여 통장 조회 (execute에서 사용)
    @Query("""
    SELECT a FROM Assets a
    WHERE a.user.id = :userId
      AND a.isSalary = true
      AND a.deletedAt IS NULL
    """)
    Optional<Assets> findByUserIdAndIsSalaryTrue(@Param("userId") UUID userId);

    // 사용자의 전체 계좌 조회 (soft delete 제외)
    @Query("""
        SELECT a FROM Assets a
        WHERE a.user.id = :userId
          AND a.deletedAt IS NULL
        ORDER BY a.accountPurpose
        """)
    List<Assets> findByUserIdAndDeletedAtIsNull(@Param("userId") UUID userId);

    // 단건 조회 (소유권 검증 포함)
    @Query("""
        SELECT a FROM Assets a
        WHERE a.id = :id
          AND a.user.id = :userId
          AND a.deletedAt IS NULL
        """)
    Optional<Assets> findByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    // Kafka transaction-events 처리용: asset_number 로 자산 조회
    // user 도 함께 사용하므로 fetch join 으로 N+1 방지
    @Query("""
        SELECT a FROM Assets a
        JOIN FETCH a.user
        WHERE a.assetNumber = :assetNumber
          AND a.deletedAt IS NULL
        """)
    Optional<Assets> findByAssetNumber(@Param("assetNumber") String assetNumber);

    // 기존에 findByUserIdAndIsSalaryTrue() 있으면 아래 것도 추가
    Optional<Assets> findByUserIdAndIsSalaryTrueAndDeletedAtIsNull(UUID userId);

}
