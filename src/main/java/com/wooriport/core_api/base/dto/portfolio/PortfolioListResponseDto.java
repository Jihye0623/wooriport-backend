package com.wooriport.core_api.base.dto.portfolio;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioListResponseDto {

    private List<PortfolioItem> portfolios;
    private Long totalAmount;           // 총 이체 금액
    private Long monthlyInvestAmount;   // 투자할 돈 (users.monthly_invest_amount)
    private Long salary;                // users.salary

    @Getter
    @Builder
    public static class PortfolioItem {
        private UUID id;
        private String assetType;      // STOCK / BOND / CASH / IRP / EMERGENCY / FIXED
        private Long assetAmount;
        private Boolean isLinked;
        private String institution;
        private String assetNumber;
        private Long balance;
        private String accountPurpose; // assets.account_purpose
    }
}