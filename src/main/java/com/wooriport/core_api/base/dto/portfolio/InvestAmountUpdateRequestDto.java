package com.wooriport.core_api.base.dto.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class InvestAmountUpdateRequestDto {

    @NotNull
    @Positive
    private Long monthlyInvestAmount;

    // null이면 기존 비율로 자동 재계산, 전달하면 해당 금액/계좌로 업데이트
    private List<PortfolioAmountItem> portfolios;

    @Getter
    public static class PortfolioAmountItem {
        @NotNull
        private UUID assetId;

        @NotNull
        @PositiveOrZero
        private Long assetAmount;

        private String accountPurpose;
    }
}
