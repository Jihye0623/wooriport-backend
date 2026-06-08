package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.Products;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Products, UUID> {

    // soft delete 제외 전체 상품 (asset-portfolio 화면 — 상품 선택 모달)
    @Query("""
        SELECT p FROM Products p
        WHERE p.deletedAt IS NULL
        ORDER BY p.productType, p.institution, p.name
        """)
    List<Products> findAllActive();

    Optional<Products> findFirstByTicker(String ticker);
}
