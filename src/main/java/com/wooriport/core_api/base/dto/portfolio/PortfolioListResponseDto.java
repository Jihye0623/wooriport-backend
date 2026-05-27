package com.wooriport.core_api.base.dto.portfolio;

import lombok.Builder;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioListResponseDto {

    private List<PortfolioItem> portfolios;
    private Long totalAmount;       // 총 이체 금액
    private Long monthlyInvestAmount;   // 투자할 돈 (users.monthly_invest_amount)

    @Getter
    @Builder
    public static class PortfolioItem {
        private UUID id;
        private String assetType;    // STOCK / BOND / CASH / IRP / EMERGENCY / FIXED
        private Long assetAmount;  // 비율 (%)
        private Boolean isLinked;    // 계좌 연동 여부
        private String institution;  // 연동 기관명
        private String assetNumber;  // 연동 계좌번호
        private Long balance;        // 현재 잔액
    }
}