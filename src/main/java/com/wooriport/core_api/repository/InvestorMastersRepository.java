package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.InvestorMasters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvestorMastersRepository extends JpaRepository<InvestorMasters, UUID> {

    @Query("SELECT m FROM InvestorMasters m LEFT JOIN FETCH m.items WHERE m.portiType = :portiType")
    Optional<InvestorMasters> findByPortiTypeWithItems(@Param("portiType") String portiType);
}
