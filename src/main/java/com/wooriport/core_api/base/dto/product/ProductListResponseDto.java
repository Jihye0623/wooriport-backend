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
        private String productType;        // SAVING(적금) / DEPOSIT(예금) / STOCK(주식) / BOND(채권) / IRP / ETF / PENSION_SAVINGS
        private String institution;
        private String name;
        private Float interestRate;
        private String description;
    }
}
