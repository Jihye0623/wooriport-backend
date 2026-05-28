package com.wooriport.core_api.service;

import com.wooriport.core_api.base.dto.product.ProductListResponseDto;
import com.wooriport.core_api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductListResponseDto getAllProducts() {
        List<ProductListResponseDto.ProductDto> products = productRepository.findAllActive().stream()
                .map(p -> ProductListResponseDto.ProductDto.builder()
                        .id(p.getId())
                        .productType(p.getProductType() != null ? p.getProductType().name() : null)
                        .institution(p.getInstitution())
                        .name(p.getName())
                        .interestRate(p.getInterestRate())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());

        return ProductListResponseDto.builder()
                .products(products)
                .build();
    }
}
