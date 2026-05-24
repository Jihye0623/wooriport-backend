package com.wooriport.core_api.base.dto.portfolioItems;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
public class PortfolioItemUpdateRequestDto {

    @NotNull
    private List<ItemDto> items;

    @Getter
    public static class ItemDto {
        @NotNull
        private String productType;  // STOCK / BOND / DEPOSIT
        @NotNull
        private Integer productRatio; // 비율 (%)
        private UUID assetId;         // 연동 계좌 (null 허용)
    }
}