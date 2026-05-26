package com.wooriport.core_api.base.dto.portfolio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
public class PortfolioUpdateRequestDto {

    private Long monthlyInvestAmount;   // 투자할 돈 (users.monthly_invest_amount)

    @NotNull
    private List<PortfolioItem> portfolios;

    @Getter
    public static class PortfolioItem {
        @NotNull
        private String assetType;   // STOCK / BOND / CASH / IRP / EMERGENCY / FIXED
        @NotNull
        private Long assetAmount; // 이체 금액
        private UUID assetId;       // 연동 계좌 (null 허용)
    }
}