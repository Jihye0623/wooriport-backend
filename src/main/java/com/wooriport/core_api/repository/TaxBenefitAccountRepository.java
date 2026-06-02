package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.TaxBenefitAccounts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaxBenefitAccountRepository extends JpaRepository<TaxBenefitAccounts, UUID> {

    @Query("""
        SELECT t FROM TaxBenefitAccounts t
        WHERE t.asset.id IN :assetIds
        """)
    List<TaxBenefitAccounts> findByAssetIdIn(@Param("assetIds") List<UUID> assetIds);
}
