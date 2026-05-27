package com.wooriport.core_api.repository;

import com.wooriport.core_api.domain.ProductCategoryRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductCategoryRateRepository extends JpaRepository<ProductCategoryRate, UUID> {
}
