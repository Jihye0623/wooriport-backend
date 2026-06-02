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

    // 업서트용: soft-delete 포함 전체 조회 (asset_number 매칭 시 복원하기 위함)
    @Query("""
        SELECT a FROM Assets a
        WHERE a.user.id = :userId
        """)
    List<Assets> findAllByUserIdIncludingDeleted(@Param("userId") UUID userId);

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

    // 세제혜택 화면 - ISA / IRP / 연금저축펀드 계좌만 조회 (soft delete 제외)
    @Query("""
        SELECT a FROM Assets a
        WHERE a.user.id = :userId
          AND a.deletedAt IS NULL
          AND a.assetType IN (
              com.wooriport.core_api.domain.Assets.AccountType.ISA,
              com.wooriport.core_api.domain.Assets.AccountType.IRP,
              com.wooriport.core_api.domain.Assets.AccountType.PENSION_SAVINGS)
        ORDER BY a.assetType
        """)
    List<Assets> findTaxBenefitAccounts(@Param("userId") UUID userId);

    // asset-portfolio 화면 - 끌어오기/모으기 통장 후보 (정책 완화 후)
    //  제외 조건:
    //   - 카드 (CREDIT_CARD / DEBIT_CARD)
    //   - soft delete
    //  portfolios.asset_id / portfolio_flows.gathering_asset_id 중복 여부는
    //  클라이언트가 현재 flows state 기준으로 동적 필터링한다.
    @Query("""
        SELECT a FROM Assets a
        WHERE a.user.id = :userId
          AND a.deletedAt IS NULL
          AND a.assetType NOT IN (
              com.wooriport.core_api.domain.Assets.AccountType.CREDIT_CARD,
              com.wooriport.core_api.domain.Assets.AccountType.DEBIT_CARD)
        ORDER BY a.institution, a.accountName
        """)
    List<Assets> findAvailableForFlows(@Param("userId") UUID userId);

}
