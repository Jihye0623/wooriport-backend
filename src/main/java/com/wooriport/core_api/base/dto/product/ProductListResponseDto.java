package com.wooriport.core_api.base.dto.product;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ProductListResponseDto {

    private List<ProductDto> products;

    @Getter
    @Builder
    public static class ProductDto {
        private UUID id;
        private String productType;    // SAVING / DEPOSIT / STOCK / BOND / IRP
        private String institution;
        private String name;
        private Float interestRate;
        private String description;
    }
}
