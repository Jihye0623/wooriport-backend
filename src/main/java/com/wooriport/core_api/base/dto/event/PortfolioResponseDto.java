package com.wooriport.core_api.base.dto.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PortfolioResponseDto {
    private String portfolioDetail;
    private PortfolioComposition portfolioComposition;

    @Getter
    @Builder
    public static class PortfolioComposition {
        private Float cashPct;       // 현금 비율
        private Float stocksEtfPct;  // 주식/ETF 비율
        private Float bondsPct;      // 채권 비율
    }
}
